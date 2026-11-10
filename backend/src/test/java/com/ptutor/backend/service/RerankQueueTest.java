package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;
import com.ptutor.backend.service.RerankQueue.Pair;

class RerankQueueTest {

    private static final String MODEL = "bge-reranker-v2-m3-int8";
    private static final RecommendationCandidateType TYPE = RecommendationCandidateType.TEACHING_REQUEST;

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final RecommendationRerankCacheRepository cache = mock(RecommendationRerankCacheRepository.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    /** The worker thread, run by hand, and a clock that only moves when the worker sleeps. */
    private final List<Runnable> worker = new ArrayList<>();
    private final long[] nowMs = {0};
    private final List<Runnable> onSleep = new ArrayList<>();
    private final RerankQueue queue = new RerankQueue(client, cache,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), settings, worker::add,
            () -> nowMs[0] * 1_000_000, millis -> {
                nowMs[0] += millis;
                onSleep.forEach(Runnable::run);
            });

    @BeforeEach
    void setUp() {
        // Every document gets a score that identifies it: "posting 3" -> 0.3
        when(client.rerank(any(), any(), any())).thenAnswer(invocation -> {
            List<String> documents = invocation.getArgument(1);
            return Optional.of(new RerankResult(MODEL, documents.stream()
                    .map(document -> Integer.parseInt(document.substring(8)) / 10.0).toList()));
        });
    }

    @Test
    void queuesAPairOnceAndScoresAFewPairsOfTheSameTextPerCall() {
        Pair a = pair("Toán", 1);
        Pair b = pair("Toán", 2);
        Pair c = pair("Toán", 3);
        Pair d = pair("Văn", 4);

        assertThat(queue.enqueue(List.of(a, b, c))).isEqualTo(3);
        assertThat(queue.enqueue(List.of(a, d))).as("a is already waiting").isEqualTo(1);
        assertThat(worker).as("one worker for the whole queue").hasSize(1);
        verify(client, never()).rerank(any(), any(), any());

        worker.get(0).run();

        ArgumentCaptor<String> queries = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> documents = ArgumentCaptor.captor();
        verify(client, times(3)).rerank(queries.capture(), documents.capture(), any());
        assertThat(queries.getAllValues()).containsExactly("Toán", "Toán", "Văn");
        assertThat(documents.getAllValues()).containsExactly(
                List.of("posting 1", "posting 2"), List.of("posting 3"), List.of("posting 4"));
        verify(cache).upsert(eq("hash(Toán)"), eq("TEACHING_REQUEST"), eq(c.candidateId()), eq("hash(posting 3)"),
                eq(MODEL), eq(new BigDecimal("0.3000")), any());
        verify(cache, times(4)).upsert(any(), any(), any(), any(), any(), any(), any());
        assertThat(queue.size()).isZero();

        assertThat(queue.enqueue(List.of(a))).as("scored pairs may be asked for again").isEqualTo(1);
        assertThat(worker).as("the worker starts again once it has stopped").hasSize(2);
    }

    @Test
    void aPairBeingScoredIsNotQueuedAgain() {
        Pair a = pair("Toán", 1);
        doAnswer(invocation -> {
            assertThat(queue.enqueue(List.of(a))).isZero();
            return Optional.of(new RerankResult(MODEL, List.of(0.1)));
        }).when(client).rerank(any(), any(), any());
        queue.enqueue(List.of(a));

        worker.get(0).run();

        verify(client, times(1)).rerank(any(), any(), any());
        assertThat(worker).hasSize(1);
    }

    @Test
    void aCallThatFailsIsDroppedAndTheWorkerGoesOn() {
        Pair a = pair("Toán", 1);
        Pair b = pair("Văn", 2);
        doReturn(Optional.empty()).when(client).rerank(eq("Toán"), any(), any());
        queue.enqueue(List.of(a, b));

        worker.get(0).run();

        verify(cache, times(1)).upsert(any(), any(), eq(b.candidateId()), any(), any(), any(), any());
        verify(cache, never()).upsert(any(), any(), eq(a.candidateId()), any(), any(), any(), any());
        assertThat(queue.size()).isZero();
    }

    @Test
    void theWorkerWaitsWhileASearchRunsAndUntilSearchesHaveBeenQuietFor2Seconds() {
        List<Long> calledAtMs = new ArrayList<>();
        doAnswer(invocation -> {
            calledAtMs.add(nowMs[0]);
            return Optional.of(new RerankResult(MODEL, List.of(0.1)));
        }).when(client).rerank(any(), any(), any());
        RerankQueue.Running search = queue.searchRunning();
        queue.enqueue(List.of(pair("Toán", 1)));
        // The search takes 5 s, longer than the quiet period: the worker must not slip in during it.
        onSleep.add(() -> {
            if (nowMs[0] >= 5_000) {
                search.close();
            }
        });

        worker.get(0).run();

        assertThat(calledAtMs).singleElement().satisfies(at -> assertThat(at).isGreaterThanOrEqualTo(5_000 + 2_000));
    }

    @Test
    void aPairThatWaitedTooLongIsScoredAloneEvenWhileSearchesRun() {
        ReflectionTestUtils.setField(settings, "rerankWorkerMaxWaitMs", 60_000L);
        List<Long> calledAtMs = new ArrayList<>();
        doAnswer(invocation -> {
            calledAtMs.add(nowMs[0]);
            List<String> documents = invocation.getArgument(1);
            return Optional.of(new RerankResult(MODEL, documents.stream().map(document -> 0.1).toList()));
        }).when(client).rerank(any(), any(), any());
        queue.searchRunning();  // searches never stop
        queue.enqueue(List.of(pair("Toán", 1), pair("Toán", 2)));

        worker.get(0).run();

        ArgumentCaptor<List<String>> documents = ArgumentCaptor.captor();
        verify(client, times(2)).rerank(any(), documents.capture(), any());
        assertThat(documents.getAllValues()).as("one pair at a time").allSatisfy(call -> assertThat(call).hasSize(1));
        assertThat(calledAtMs).allSatisfy(at -> assertThat(at).isGreaterThanOrEqualTo(60_000));
        assertThat(queue.size()).isZero();
    }

    @Test
    void aFullQueueDropsNewPairs() {
        ReflectionTestUtils.setField(settings, "rerankQueueCapacity", 2);

        assertThat(queue.enqueue(List.of(pair("Toán", 1), pair("Toán", 2), pair("Toán", 3)))).isEqualTo(2);
        assertThat(queue.size()).isEqualTo(2);
    }

    private Pair pair(String query, int posting) {
        return new Pair(query, "hash(" + query + ")", TYPE, UUID.randomUUID(), "posting " + posting,
                "hash(posting " + posting + ")");
    }
}
