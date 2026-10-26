package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RerankMode;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Phase 4 of the ranking (design 06, 6.9): a cross-encoder reads the searcher's text together with
 * the text of each of the best postings and says how relevant the posting is. Scores are cached by
 * (text of the searcher, posting, content of the posting, model), so a pair is computed once.
 *
 * <p>The model is slow on a CPU (~0.9 s a pair), so by default a search does not wait for it
 * ({@code rerank.mode = ASYNC}, design 13, 13.2): it uses the scores already cached, the cosine part for the
 * rest, and queues the missing pairs for {@link RerankQueue}. In ONLINE mode the pairs are sent a few at a
 * time, best ranked first, until the time budget is used up; what was not scored in time is queued too.
 * The nightly prefetch scores synchronously with a generous budget. Reranking never makes a search fail.
 */
@Service
@RequiredArgsConstructor
public class RerankService {

    /** The AI service refuses longer texts; it only reads the first few hundred tokens anyway. */
    static final int MAX_TEXT_CHARS = 8000;

    public enum Status {
        /** Every pair of the top group was already in the cache. */
        CACHE,
        /** At least one pair was computed for this call and the whole top group has a score. */
        ONLINE,
        /** Time ran out or the AI service failed: only part of the top group (or none) has a score. */
        TIMEOUT,
        /** ASYNC: part of the top group was cached; the rest is queued. */
        PARTIAL,
        /** ASYNC: nothing of the top group was cached; all of it is queued. */
        NONE,
        DISABLED
    }

    /** @param computed how many pairs were scored by the model during this call */
    public record Result(Map<UUID, Double> scores, Status status, int computed) {
    }

    private final AiServiceClient aiServiceClient;
    private final RerankQueue queue;
    private final RecommendationRerankCacheRepository cacheRepository;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final EntityManager entityManager;
    private final RecommendationSettings settings;

    /** For a search: the best {@code rerank.top-k} postings within {@code rerank.timeout-ms}. */
    public Result forSearch(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds) {
        Duration budget = settings.getRerankMode() == RerankMode.ASYNC
                ? null : Duration.ofMillis(settings.getRerankTimeoutMs());
        return rerank(queryText, type, rankedCandidateIds, settings.getRerankTopK(), budget);
    }

    /** For the nightly job: a few more postings than a search uses, with a generous time budget. */
    public Result prefetch(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds) {
        return rerank(queryText, type, rankedCandidateIds, settings.getRerankPrefetchTopK(),
                Duration.ofMillis(settings.getRerankPrefetchTimeoutMs()));
    }

    /** Wraps a search, so that the background worker waits until searches are quiet ({@link RerankQueue}). */
    public RerankQueue.Running searchRunning() {
        return queue.searchRunning();
    }

    public int deleteOlderThan(LocalDateTime threshold) {
        return cacheRepository.deleteByComputedAtBefore(threshold);
    }

    /** @param budget how long the model may be waited for; null: not at all, the missing pairs are queued */
    Result rerank(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds, int topK,
            Duration budget) {
        if (!settings.isRerankEnabled() || queryText == null || queryText.isBlank()) {
            return new Result(Map.of(), Status.DISABLED, 0);
        }
        String query = limit(queryText.strip());
        String queryHash = textBuilder.hash(query);
        String model = settings.getRerankModelVersion();

        Map<UUID, String> texts = new LinkedHashMap<>();
        Map<UUID, String> contentHashes = new LinkedHashMap<>();
        List<UUID> top = rankedCandidateIds.stream().limit(Math.max(0, topK)).toList();
        Map<UUID, StoredText> stored = storedTexts(type, top);
        for (UUID id : top) {
            StoredText saved = stored.get(id);
            if (saved != null) {
                texts.put(id, saved.text());
                contentHashes.put(id, saved.hash());
            } else {
                builtText(type, id).filter(text -> !text.isBlank()).map(this::limit).ifPresent(text -> {
                    texts.put(id, text);
                    contentHashes.put(id, textBuilder.hash(text));
                });
            }
        }

        Map<UUID, Double> scores = new LinkedHashMap<>();
        if (!texts.isEmpty()) {
            for (RecommendationRerankCache row : cacheRepository.findScores(queryHash, type, model, texts.keySet())) {
                UUID id = row.getId().getCandidateId();
                // A posting edited since it was scored has another content hash: the old score is stale.
                if (row.getCandidateContentHash().equals(contentHashes.get(id))) {
                    scores.put(id, row.getRerankScore().doubleValue());
                }
            }
        }
        List<UUID> missing = texts.keySet().stream().filter(id -> !scores.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return new Result(scores, Status.CACHE, 0);
        }
        if (budget == null) {
            queue.enqueue(pairs(query, queryHash, type, missing, texts, contentHashes));
            return new Result(scores, scores.isEmpty() ? Status.NONE : Status.PARTIAL, 0);
        }

        long deadline = System.nanoTime() + budget.toNanos();
        int computed = 0;
        int chunkSize = Math.max(1, settings.getRerankChunkSize());
        for (int start = 0; start < missing.size(); start += chunkSize) {
            long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
            if (remainingMs <= 0) {
                break;
            }
            List<UUID> chunk = missing.subList(start, Math.min(start + chunkSize, missing.size()));
            Optional<RerankResult> answer = aiServiceClient.rerank(
                    query, chunk.stream().map(texts::get).toList(), Duration.ofMillis(remainingMs));
            if (answer.isEmpty()) {
                break;
            }
            LocalDateTime now = LocalDateTime.now();
            for (int index = 0; index < chunk.size(); index++) {
                UUID id = chunk.get(index);
                // Rounded like the stored column, so a cached score and a fresh one rank identically.
                BigDecimal score = BigDecimal.valueOf(answer.get().scores().get(index))
                        .setScale(4, RoundingMode.HALF_UP);
                cacheRepository.upsert(queryHash, type.name(), id, contentHashes.get(id), answer.get().model(),
                        score, now);
                scores.put(id, score.doubleValue());
                computed++;
            }
        }
        if (scores.size() == texts.size()) {
            return new Result(scores, Status.ONLINE, computed);
        }
        // Scored later in the background rather than thrown away (design 13, 13.2).
        queue.enqueue(pairs(query, queryHash, type,
                missing.stream().filter(id -> !scores.containsKey(id)).toList(), texts, contentHashes));
        return new Result(scores, Status.TIMEOUT, computed);
    }

    private static List<RerankQueue.Pair> pairs(String query, String queryHash, RecommendationCandidateType type,
            List<UUID> ids, Map<UUID, String> texts, Map<UUID, String> contentHashes) {
        return ids.stream()
                .map(id -> new RerankQueue.Pair(query, queryHash, type, id, texts.get(id), contentHashes.get(id)))
                .toList();
    }

    /**
     * The text each candidate was embedded from and its hash, in one query (design 13, 13.2). It is the text
     * {@link #builtText} would give: {@code EmbeddingJobProcessor} builds it the same way and
     * {@link EmbeddingTextBuilder} keeps it under {@link #MAX_TEXT_CHARS}. Building it again for ten postings took
     * ~120 ms of every search. A posting edited since keeps its old text until it is embedded again, like its
     * vector, and the new hash then retires its cached score. Candidates never embedded are left out.
     */
    private Map<UUID, StoredText> storedTexts(RecommendationCandidateType type, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String entity = type == RecommendationCandidateType.TEACHING_REQUEST ? "TeachingRequest" : "StudyingRequest";
        List<Object[]> rows = entityManager.createQuery("select r.id, r.embeddingSourceText, r.embeddingContentHash"
                + " from " + entity + " r where r.id in :ids", Object[].class).setParameter("ids", ids).getResultList();
        Map<UUID, StoredText> found = new HashMap<>();
        for (Object[] row : rows) {
            if (row[1] instanceof String text && row[2] instanceof String hash && !text.isBlank()
                    && text.length() <= MAX_TEXT_CHARS) {
                found.put((UUID) row[0], new StoredText(text, hash));
            }
        }
        return found;
    }

    private record StoredText(String text, String hash) {
    }

    /** The same text the posting is embedded from, so both models judge the same content. */
    Optional<String> builtText(RecommendationCandidateType type, UUID id) {
        return switch (type) {
            case TEACHING_REQUEST -> Optional.ofNullable(entityManager.find(TeachingRequest.class, id))
                    .map(request -> textBuilder.forTeachingRequest(request, tagNames(
                            RecommendationEntityType.TEACHING_REQUEST, id, RecommendationEntityType.TUTOR,
                            request.getTutor() == null ? null : request.getTutor().getId())));
            case STUDYING_REQUEST -> Optional.ofNullable(entityManager.find(StudyingRequest.class, id))
                    .map(request -> textBuilder.forStudyingRequest(request, tagNames(
                            RecommendationEntityType.STUDYING_REQUEST, id, RecommendationEntityType.STUDENT,
                            request.getStudent() == null ? null : request.getStudent().getId())));
        };
    }

    private List<String> tagNames(
            RecommendationEntityType type, UUID id, RecommendationEntityType ownerType, UUID ownerId) {
        List<String> names = new ArrayList<>();
        entityTagRepository.findAllByEntityTypeAndEntityId(type, id)
                .forEach(tag -> names.add(tag.getTag().getName()));
        if (ownerId != null) {
            entityTagRepository.findAllByEntityTypeAndEntityId(ownerType, ownerId)
                    .forEach(tag -> names.add(tag.getTag().getName()));
        }
        return names;
    }

    private String limit(String text) {
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }
}
