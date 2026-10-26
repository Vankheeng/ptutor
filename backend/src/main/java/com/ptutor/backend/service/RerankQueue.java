package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;

import jakarta.annotation.PreDestroy;

/**
 * Pairs a search found missing in the rerank cache, scored one call at a time by a single background thread
 * (design 13, 13.2). A pair is queued once, however many searches ask for it before it is scored; the score
 * goes to the same cache the searches read, so the next search, a page or a match alert uses it.
 *
 * <p>The worker gives way to searches: it scores only when no search is running and none has finished for
 * {@code rerank.worker-quiet-ms}. On a laptop the two models slow each other down even on separate cores (a
 * search waits for its query embedding), so they should not run at the same time. A pair that has waited
 * {@code rerank.worker-max-wait-ms} is scored anyway, one at a time, so that busy hours cannot starve the queue.
 * With reranking on a machine of its own this would not be needed (design 13, 13.2).
 *
 * <p>The queue lives in memory: after a restart the next search, or the nightly prefetch, asks again.
 */
@Component
public class RerankQueue {

    private static final Logger log = LoggerFactory.getLogger(RerankQueue.class);

    /** One pair to score. The texts are taken when the search ran, so the worker reads no entity. */
    public record Pair(String query, String queryHash, RecommendationCandidateType type, UUID candidateId,
            String text, String contentHash) {

        Key key() {
            return new Key(queryHash, type, candidateId, contentHash);
        }
    }

    record Key(String queryHash, RecommendationCandidateType type, UUID candidateId, String contentHash) {
    }

    private record Waiting(Pair pair, long sinceNanos) {
    }

    /** A search in progress; closing it lets the worker go on once things are quiet. */
    public interface Running extends AutoCloseable {
        @Override
        void close();
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Longest single wait of the worker, so that it notices quickly that searches have stopped. */
    static final long POLL_MS = 200;

    private final AiServiceClient aiServiceClient;
    private final RecommendationRerankCacheRepository cacheRepository;
    private final TransactionTemplate transaction;
    private final RecommendationSettings settings;
    private final Executor executor;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;

    /** Waiting pairs, oldest first, and the ones being scored right now; all of it guarded by {@code this}. */
    private final Map<Key, Waiting> pending = new LinkedHashMap<>();
    private final List<Key> inFlight = new ArrayList<>();
    private boolean draining;
    private int runningSearches;
    private long lastSearchEndNanos;

    @Autowired
    public RerankQueue(AiServiceClient aiServiceClient, RecommendationRerankCacheRepository cacheRepository,
            PlatformTransactionManager transactionManager, RecommendationSettings settings) {
        this(aiServiceClient, cacheRepository, new TransactionTemplate(transactionManager), settings,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "rerank-worker");
                    thread.setDaemon(true);
                    return thread;
                }), System::nanoTime, Thread::sleep);
    }

    RerankQueue(AiServiceClient aiServiceClient, RecommendationRerankCacheRepository cacheRepository,
            TransactionTemplate transaction, RecommendationSettings settings, Executor executor,
            LongSupplier nanoClock, Sleeper sleeper) {
        this.aiServiceClient = aiServiceClient;
        this.cacheRepository = cacheRepository;
        this.transaction = transaction;
        this.settings = settings;
        this.executor = executor;
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
        this.lastSearchEndNanos = nanoClock.getAsLong();
    }

    /** Marks a search as running until the returned handle is closed (use with try-with-resources). */
    public Running searchRunning() {
        synchronized (this) {
            runningSearches++;
        }
        boolean[] closed = new boolean[1];
        return () -> {
            synchronized (this) {
                if (!closed[0]) {
                    closed[0] = true;
                    runningSearches--;
                    lastSearchEndNanos = nanoClock.getAsLong();
                }
            }
        };
    }

    /** @return how many of the pairs were new to the queue */
    public int enqueue(List<Pair> pairs) {
        int added = 0;
        boolean start;
        synchronized (this) {
            for (Pair pair : pairs) {
                Key key = pair.key();
                if (pending.containsKey(key) || inFlight.contains(key)) {
                    continue;
                }
                if (pending.size() >= settings.getRerankQueueCapacity()) {
                    log.warn("Rerank queue is full ({} pairs): the rest of this search is dropped", pending.size());
                    break;
                }
                pending.put(key, new Waiting(pair, nanoClock.getAsLong()));
                added++;
            }
            start = added > 0 && !draining;
            if (start) {
                draining = true;
            }
        }
        if (start) {
            executor.execute(this::drain);
        }
        return added;
    }

    public synchronized int size() {
        return pending.size() + inFlight.size();
    }

    /** Scores the queue until it is empty: one call at a time, a few pairs of the same text per call. */
    void drain() {
        while (true) {
            Boolean starved = waitForTurn();
            if (starved == null) {
                return;
            }
            List<Pair> chunk = next(starved);
            if (chunk.isEmpty()) {
                return;
            }
            try {
                score(chunk);
            } catch (RuntimeException exception) {
                // A pair that cannot be stored must not stop the worker; a later search asks again.
                log.warn("Rerank worker failed for {} pairs: {}", chunk.size(), exception.toString());
            } finally {
                synchronized (this) {
                    inFlight.clear();
                }
            }
        }
    }

    /** Waits until searches are quiet. @return whether the oldest pair is scored anyway; null when interrupted */
    private Boolean waitForTurn() {
        while (true) {
            long waitMs;
            synchronized (this) {
                long now = nanoClock.getAsLong();
                long quietMs = (now - lastSearchEndNanos) / 1_000_000;
                if (pending.isEmpty() || runningSearches == 0 && quietMs >= settings.getRerankWorkerQuietMs()) {
                    return false;
                }
                long oldestMs = (now - pending.values().iterator().next().sinceNanos()) / 1_000_000;
                if (oldestMs >= settings.getRerankWorkerMaxWaitMs()) {
                    return true;
                }
                waitMs = runningSearches > 0 ? POLL_MS
                        : Math.max(1, Math.min(POLL_MS, settings.getRerankWorkerQuietMs() - quietMs));
            }
            try {
                sleeper.sleep(waitMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                synchronized (this) {
                    draining = false;
                }
                return null;
            }
        }
    }

    /** @param starved true: only the oldest pair, scored although searches are running */
    private synchronized List<Pair> next(boolean starved) {
        List<Pair> chunk = new ArrayList<>();
        int size = starved ? 1 : Math.max(1, settings.getRerankWorkerChunkSize());
        Iterator<Waiting> iterator = pending.values().iterator();
        while (iterator.hasNext() && chunk.size() < size) {
            Pair pair = iterator.next().pair();
            if (chunk.isEmpty() || chunk.get(0).queryHash().equals(pair.queryHash())) {
                chunk.add(pair);
                iterator.remove();
                inFlight.add(pair.key());
            }
        }
        if (chunk.isEmpty()) {
            draining = false;
            log.debug("Rerank worker: queue empty");
        } else {
            log.debug("Rerank worker: scoring {} pairs{}, {} waiting", chunk.size(),
                    starved ? " (waited too long)" : "", pending.size());
        }
        return chunk;
    }

    private void score(List<Pair> chunk) {
        Optional<RerankResult> answer = aiServiceClient.rerank(chunk.get(0).query(),
                chunk.stream().map(Pair::text).toList(), Duration.ofMillis(settings.getRerankPrefetchTimeoutMs()));
        if (answer.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        transaction.executeWithoutResult(status -> {
            for (int index = 0; index < chunk.size(); index++) {
                Pair pair = chunk.get(index);
                // Rounded like the stored column, as in RerankService.
                BigDecimal score = BigDecimal.valueOf(answer.get().scores().get(index)).setScale(4, RoundingMode.HALF_UP);
                cacheRepository.upsert(pair.queryHash(), pair.type().name(), pair.candidateId(), pair.contentHash(),
                        answer.get().model(), score, now);
            }
        });
    }

    @PreDestroy
    void stop() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}
