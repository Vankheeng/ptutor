package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;
import com.ptutor.backend.service.RerankService.Result;
import com.ptutor.backend.service.RerankService.Status;

import jakarta.persistence.EntityManager;

class RerankServiceTest {

    private static final String MODEL = "bge-reranker-v2-m3-int8";
    private static final RecommendationCandidateType TYPE = RecommendationCandidateType.TEACHING_REQUEST;

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final RecommendationRerankCacheRepository cache = mock(RecommendationRerankCacheRepository.class);
    private final RecommendationEntityTagRepository entityTags = mock(RecommendationEntityTagRepository.class);
    private final EmbeddingTextBuilder textBuilder = mock(EmbeddingTextBuilder.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final RerankService service =
            new RerankService(client, cache, entityTags, textBuilder, entityManager, settings);

    private final List<UUID> ids = new ArrayList<>();

    @BeforeEach
    void setUp() {
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
