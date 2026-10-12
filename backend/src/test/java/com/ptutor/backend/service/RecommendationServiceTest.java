package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EmbeddingResult;
import com.ptutor.backend.repository.CandidateRepository.Hit;

class RecommendationServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Test
    void scoresEveryHitWhenThereAreNoMoreThanTheLimit() {
        List<Hit> hits = hits(5, 2);

        assertThat(service(10, 3).selectHits(hits)).isEqualTo(hits);
    }

    @Test
    void keepsTheNearestHitsAndReservesPlacesForTheNewestPostingsWithoutEmbedding() {
        List<Hit> hits = hits(20, 6);

        List<Hit> selected = service(10, 3).selectHits(hits);

        assertThat(selected).hasSize(10);
        assertThat(selected.subList(0, 7)).as("7 nearest by cosine, best first")
                .extracting(Hit::cosine).containsExactly(0.99, 0.98, 0.97, 0.96, 0.95, 0.94, 0.93);
        assertThat(selected.subList(7, 10)).as("3 newest without embedding")
                .allMatch(hit -> hit.cosine() == null)
                .extracting(Hit::createdAt)
                .containsExactly(BASE.plusDays(6), BASE.plusDays(5), BASE.plusDays(4));
    }

    @Test
    void fillsUpWithUnembeddedPostingsWhenFewHaveAnEmbedding() {
        List<Hit> selected = service(10, 3).selectHits(hits(4, 30));

        assertThat(selected).hasSize(10);
        assertThat(selected.stream().filter(hit -> hit.cosine() != null)).hasSize(4);
    }

    @Test
    void usesFewerReservedPlacesWhenFewPostingsLackAnEmbedding() {
        List<Hit> selected = service(10, 3).selectHits(hits(30, 1));

        assertThat(selected).hasSize(10);
        assertThat(selected.stream().filter(hit -> hit.cosine() == null)).hasSize(1);
    }

    @Test
    void queryEmbeddingIsCachedByTextAndNotCachedWhenTheAiServiceFails() {
        AiServiceClient client = mock(AiServiceClient.class);
        float[] vector = new float[AiServiceClient.EMBEDDING_DIMENSIONS];
        when(client.embed(any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new EmbeddingResult("bge-m3-int8", List.of(vector))));
        QueryEmbeddingService embeddings = new QueryEmbeddingService(
                client, new EmbeddingTextBuilder(), new RecommendationSettings());

        assertThat(embeddings.embed("Môn Toán. Lớp 9")).as("failure is not cached").isEmpty();
        assertThat(embeddings.embed("Môn Toán. Lớp 9")).containsSame(vector);
        assertThat(embeddings.embed("Môn Toán. Lớp 9")).containsSame(vector);
        assertThat(embeddings.embed("  ")).isEmpty();

        verify(client, times(2)).embed(any(), any());
    }

    private RecommendationService service(int candidateLimit, int unembeddedQuota) {
        RecommendationSettings settings = new RecommendationSettings();
        ReflectionTestUtils.setField(settings, "candidateLimit", candidateLimit);
        ReflectionTestUtils.setField(settings, "candidateUnembeddedQuota", unembeddedQuota);
        return new RecommendationService(null, null, null, null, null, null, null, settings, null);
    }

    /** {@code embedded} hits with cosine 0.99, 0.98, ... followed by {@code unembedded} hits, oldest first. */
    private List<Hit> hits(int embedded, int unembedded) {
        List<Hit> hits = new ArrayList<>();
        for (int index = 0; index < embedded; index++) {
            hits.add(new Hit(UUID.randomUUID(), BASE, Math.round((0.99 - index * 0.01) * 100) / 100.0));
        }
        for (int index = 1; index <= unembedded; index++) {
            hits.add(new Hit(UUID.randomUUID(), BASE.plusDays(index), null));
        }
        return hits;
    }
}
