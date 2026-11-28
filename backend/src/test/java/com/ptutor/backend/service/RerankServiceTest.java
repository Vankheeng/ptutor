package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.RecommendationRerankCacheId;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RerankMode;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;
import com.ptutor.backend.service.RerankService.Result;
import com.ptutor.backend.service.RerankService.Status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

class RerankServiceTest {

    private static final String MODEL = "bge-reranker-v2-m3-int8";
    private static final RecommendationCandidateType TYPE = RecommendationCandidateType.TEACHING_REQUEST;

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final RerankQueue queue = mock(RerankQueue.class);
    private final RecommendationRerankCacheRepository cache = mock(RecommendationRerankCacheRepository.class);
    private final RecommendationEntityTagRepository entityTags = mock(RecommendationEntityTagRepository.class);
    private final EmbeddingTextBuilder textBuilder = mock(EmbeddingTextBuilder.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final RerankService service =
            new RerankService(client, queue, cache, entityTags, textBuilder, entityManager, settings);

    private final List<UUID> ids = new ArrayList<>();
    /** Rows (id, embedding_source_text, embedding_content_hash) of the postings; none embedded unless a test adds. */
    private final List<Object[]> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Reranking is off by default since 26/10/2026; these tests switch it on as a deployment would.
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ASYNC);
        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> storedTexts = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(storedTexts);
        when(storedTexts.setParameter(anyString(), any())).thenReturn(storedTexts);
        when(storedTexts.getResultList()).thenReturn(stored);
        when(textBuilder.hash(any())).thenAnswer(invocation -> "hash(" + invocation.getArgument(0) + ")");
        when(entityTags.findAllByEntityTypeAndEntityId(any(), any())).thenReturn(List.of());
        for (int index = 0; index < 6; index++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            TeachingRequest request = mock(TeachingRequest.class);
            when(entityManager.find(TeachingRequest.class, id)).thenReturn(request);
            when(textBuilder.forTeachingRequest(eq(request), any())).thenReturn("posting " + index);
        }
        // Every document gets a score that identifies it: "posting 3" -> 0.3
        when(client.rerank(any(), any(), any())).thenAnswer(invocation -> {
            List<String> documents = invocation.getArgument(1);
            return Optional.of(new RerankResult(MODEL, documents.stream()
                    .map(document -> Integer.parseInt(document.substring(8)) / 10.0).toList()));
        });
    }

    @Test
    void scoresTheTopGroupInChunksBestFirstAndStoresEveryScore() {
        ReflectionTestUtils.setField(settings, "rerankChunkSize", 2);

        Result result = service.rerank("Cần gia sư Toán", TYPE, ids, 5, Duration.ofSeconds(30));

        assertThat(result.status()).isEqualTo(Status.ONLINE);
        assertThat(result.computed()).isEqualTo(5);
        assertThat(result.scores()).hasSize(5).containsEntry(ids.get(0), 0.0).containsEntry(ids.get(4), 0.4)
                .doesNotContainKey(ids.get(5));
        ArgumentCaptor<List<String>> documents = ArgumentCaptor.captor();
        verify(client, times(3)).rerank(eq("Cần gia sư Toán"), documents.capture(), any());
        assertThat(documents.getAllValues()).containsExactly(
                List.of("posting 0", "posting 1"), List.of("posting 2", "posting 3"), List.of("posting 4"));
        verify(cache).upsert(eq("hash(Cần gia sư Toán)"), eq("TEACHING_REQUEST"), eq(ids.get(3)),
                eq("hash(posting 3)"), eq(MODEL), eq(new BigDecimal("0.3000")), any());
        verify(cache, times(5)).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void usesCachedScoresAndOnlyAsksTheModelForTheRest() {
        when(cache.findScores(eq("hash(Cần gia sư Toán)"), eq(TYPE), eq(MODEL), anyCollection())).thenReturn(List.of(
                cached(ids.get(0), "hash(posting 0)", "0.9100"),
                cached(ids.get(1), "hash(what the posting said before it was edited)", "0.9900")));

        Result result = service.rerank("Cần gia sư Toán", TYPE, ids, 3, Duration.ofSeconds(30));

        assertThat(result.status()).isEqualTo(Status.ONLINE);
        assertThat(result.computed()).isEqualTo(2);
        assertThat(result.scores()).containsEntry(ids.get(0), 0.91).as("a stale score is computed again")
                .containsEntry(ids.get(1), 0.1).containsEntry(ids.get(2), 0.2);
        ArgumentCaptor<List<String>> documents = ArgumentCaptor.captor();
        verify(client).rerank(any(), documents.capture(), any());
        assertThat(documents.getValue()).containsExactly("posting 1", "posting 2");
    }

    @Test
    void doesNotCallTheModelWhenEverythingIsCached() {
        when(cache.findScores(any(), any(), any(), anyCollection())).thenReturn(List.of(
                cached(ids.get(0), "hash(posting 0)", "0.7000"), cached(ids.get(1), "hash(posting 1)", "0.2000")));

        Result result = service.rerank("Cần gia sư Toán", TYPE, ids, 2, Duration.ofSeconds(30));

        assertThat(result.status()).isEqualTo(Status.CACHE);
        assertThat(result.computed()).isZero();
        assertThat(result.scores()).containsEntry(ids.get(0), 0.7).containsEntry(ids.get(1), 0.2);
        verify(client, never()).rerank(any(), any(), any());
    }

    @Test
    void keepsWhatWasScoredBeforeTheAiServiceFailedOrTimeRanOut() {
        ReflectionTestUtils.setField(settings, "rerankChunkSize", 2);
        org.mockito.Mockito.doReturn(Optional.empty()).when(client)
                .rerank(any(), eq(List.of("posting 2", "posting 3")), any());

        Result failed = service.rerank("Cần gia sư Toán", TYPE, ids, 6, Duration.ofSeconds(30));

        assertThat(failed.status()).isEqualTo(Status.TIMEOUT);
        assertThat(failed.scores()).containsOnlyKeys(ids.get(0), ids.get(1));
        verify(client, times(2)).rerank(any(), any(), any());

        Result noTime = service.rerank("Câu khác", TYPE, ids, 6, Duration.ZERO);

        assertThat(noTime.status()).isEqualTo(Status.TIMEOUT);
        assertThat(noTime.scores()).isEmpty();
        verify(client, times(2)).rerank(any(), any(), any());

        ArgumentCaptor<List<RerankQueue.Pair>> queued = ArgumentCaptor.captor();
        verify(queue, times(2)).enqueue(queued.capture());
        assertThat(queued.getAllValues().get(0)).as("what was not scored in time is scored in the background")
                .extracting(RerankQueue.Pair::candidateId).containsExactly(ids.get(2), ids.get(3), ids.get(4), ids.get(5));
        assertThat(queued.getAllValues().get(1)).hasSize(6);
    }

    @Test
    void anAsyncSearchUsesTheCacheAndQueuesTheRestWithoutCallingTheModel() {
        ReflectionTestUtils.setField(settings, "rerankTopK", 3);
        when(cache.findScores(any(), any(), any(), anyCollection())).thenReturn(List.of(
                cached(ids.get(0), "hash(posting 0)", "0.7000")));

        Result result = service.forSearch("Cần gia sư Toán", TYPE, ids);

        assertThat(result.status()).isEqualTo(Status.PARTIAL);
        assertThat(result.computed()).isZero();
        assertThat(result.scores()).containsOnlyKeys(ids.get(0));
        verify(client, never()).rerank(any(), any(), any());
        ArgumentCaptor<List<RerankQueue.Pair>> queued = ArgumentCaptor.captor();
        verify(queue).enqueue(queued.capture());
        assertThat(queued.getValue()).containsExactly(
                new RerankQueue.Pair("Cần gia sư Toán", "hash(Cần gia sư Toán)", TYPE, ids.get(1), "posting 1",
                        "hash(posting 1)"),
                new RerankQueue.Pair("Cần gia sư Toán", "hash(Cần gia sư Toán)", TYPE, ids.get(2), "posting 2",
                        "hash(posting 2)"));
    }

    @Test
    void anAsyncSearchWithNothingCachedRanksByTheCosineAlone() {
        Result result = service.forSearch("Cần gia sư Toán", TYPE, ids);

        assertThat(result.status()).isEqualTo(Status.NONE);
        assertThat(result.scores()).isEmpty();
        verify(client, never()).rerank(any(), any(), any());
        verify(queue).enqueue(any());
    }

    @Test
    void anAsyncSearchWithEverythingCachedQueuesNothing() {
        ReflectionTestUtils.setField(settings, "rerankTopK", 1);
        when(cache.findScores(any(), any(), any(), anyCollection())).thenReturn(List.of(
                cached(ids.get(0), "hash(posting 0)", "0.7000")));

        assertThat(service.forSearch("Cần gia sư Toán", TYPE, ids).status()).isEqualTo(Status.CACHE);
        verifyNoInteractions(queue);
    }

    @Test
    void usesTheTextAndHashTheEmbeddingStoredWithoutBuildingTheTextAgain() {
        ReflectionTestUtils.setField(settings, "rerankTopK", 2);
        stored.add(new Object[] {ids.get(0), "posting 0", "stored-hash-0"});
        stored.add(new Object[] {ids.get(1), "posting 1", "stored-hash-1"});
        when(cache.findScores(any(), any(), any(), anyCollection())).thenReturn(List.of(
                cached(ids.get(0), "stored-hash-0", "0.7000"), cached(ids.get(1), "hash(posting 1)", "0.2000")));

        Result result = service.forSearch("Cần gia sư Toán", TYPE, ids);

        assertThat(result.scores()).as("the cache is checked against the stored hash")
                .containsOnlyKeys(ids.get(0));
        ArgumentCaptor<List<RerankQueue.Pair>> queued = ArgumentCaptor.captor();
        verify(queue).enqueue(queued.capture());
        assertThat(queued.getValue()).singleElement().satisfies(pair -> {
            assertThat(pair.text()).isEqualTo("posting 1");
            assertThat(pair.contentHash()).isEqualTo("stored-hash-1");
        });
        verify(entityManager, never()).find(eq(TeachingRequest.class), any());
        verifyNoInteractions(entityTags);
    }

    @Test
    void aPostingNeverEmbeddedIsBuiltTheOldWay() {
        ReflectionTestUtils.setField(settings, "rerankTopK", 2);
        stored.add(new Object[] {ids.get(0), "posting 0", "stored-hash-0"});
        stored.add(new Object[] {ids.get(1), null, null});

        service.forSearch("Cần gia sư Toán", TYPE, ids);

        ArgumentCaptor<List<RerankQueue.Pair>> queued = ArgumentCaptor.captor();
        verify(queue).enqueue(queued.capture());
        assertThat(queued.getValue()).extracting(RerankQueue.Pair::contentHash)
                .containsExactly("stored-hash-0", "hash(posting 1)");
        verify(entityManager, never()).find(TeachingRequest.class, ids.get(0));
        verify(entityManager).find(TeachingRequest.class, ids.get(1));
    }

    @Test
    void rerankingIsOffByDefault() {
        RecommendationSettings defaults = new RecommendationSettings();
        RerankService off = new RerankService(client, queue, cache, entityTags, textBuilder, entityManager, defaults);

        assertThat(defaults.getRerankMode()).isEqualTo(RerankMode.OFF);
        assertThat(off.forSearch("Cần gia sư Toán", TYPE, ids).status()).isEqualTo(Status.DISABLED);
        assertThat(off.prefetch("Cần gia sư Toán", TYPE, ids).status()).isEqualTo(Status.DISABLED);
        verifyNoInteractions(client, queue, cache);
    }

    @Test
    void theOffModeNeitherCallsTheModelNorQueues() {
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.OFF);

        assertThat(service.forSearch("Cần gia sư Toán", TYPE, ids).status()).isEqualTo(Status.DISABLED);
        assertThat(service.prefetch("Cần gia sư Toán", TYPE, ids).status()).isEqualTo(Status.DISABLED);
        verifyNoInteractions(client, cache, queue);
        assertThat(settings.isRerankEnabled()).as("the nightly prefetch reads this").isFalse();
    }

    @Test
    void theOldSwitchStillTurnsRerankingOffWhateverTheMode() {
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ONLINE);
        ReflectionTestUtils.setField(settings, "rerankEnabled", false);

        assertThat(settings.getRerankMode()).isEqualTo(RerankMode.OFF);
    }

    @Test
    void isSkippedWhenSwitchedOffOrThereIsNoText() {
        assertThat(service.rerank("  ", TYPE, ids, 5, Duration.ofSeconds(1)).status()).isEqualTo(Status.DISABLED);
        ReflectionTestUtils.setField(settings, "rerankEnabled", false);

        Result result = service.forSearch("Cần gia sư Toán", TYPE, ids);

        assertThat(result.status()).isEqualTo(Status.DISABLED);
        assertThat(result.scores()).isEmpty();
        verifyNoInteractions(client, cache);
    }

    @Test
    void aSearchUsesTheConfiguredTopGroupAndThePrefetchALargerOne() {
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ONLINE);
        ReflectionTestUtils.setField(settings, "rerankTopK", 2);
        ReflectionTestUtils.setField(settings, "rerankPrefetchTopK", 5);

        assertThat(service.forSearch("Cần gia sư Toán", TYPE, ids).scores()).hasSize(2);
        assertThat(service.prefetch("Cần gia sư Toán", TYPE, ids).scores()).hasSize(5);
    }

    @Test
    void aPostingDeletedSinceTheRankingIsLeftOut() {
        UUID gone = UUID.randomUUID();

        Result result = service.rerank("Cần gia sư Toán", TYPE, List.of(gone, ids.get(0)), 5, Duration.ofSeconds(30));

        assertThat(result.status()).isEqualTo(Status.ONLINE);
        assertThat(result.scores()).containsOnlyKeys(ids.get(0));
    }

    private RecommendationRerankCache cached(UUID candidateId, String contentHash, String score) {
        return RecommendationRerankCache.builder()
                .id(new RecommendationRerankCacheId("hash(Cần gia sư Toán)", TYPE, candidateId, MODEL))
                .candidateContentHash(contentHash).rerankScore(new BigDecimal(score)).build();
    }
}
