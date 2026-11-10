package com.ptutor.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.service.RecommendationService;
import com.ptutor.backend.entity.enums.RerankMode;
import com.ptutor.backend.service.RecommendationSettings;
import com.ptutor.backend.service.RerankService;

class RerankPrefetchJobTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final com.ptutor.backend.service.StudentRecommendationService studentRecommendationService =
            mock(com.ptutor.backend.service.StudentRecommendationService.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final RerankPrefetchJob job =
            new RerankPrefetchJob(jdbc, aiServiceClient, recommendationService, studentRecommendationService,
                    rerankService, settings);

    /** Reranking is off by default since 26/10/2026; these tests switch it on as a deployment would. */
    @BeforeEach
    void switchRerankingOn() {
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ASYNC);
    }

    @Test
    void doesNothingWithTheDefaultModeOff() {
        RecommendationSettings defaults = new RecommendationSettings();
        new RerankPrefetchJob(jdbc, aiServiceClient, recommendationService, studentRecommendationService,
                rerankService, defaults).run();

        assertThat(defaults.getRerankMode()).isEqualTo(RerankMode.OFF);
        verifyNoInteractions(jdbc, aiServiceClient, recommendationService, studentRecommendationService, rerankService);
    }

    @Test
    void prefetchesEveryOpenStudyingRequestAndKeepsGoingWhenOneFails() {
        UUID firstUser = UUID.randomUUID();
        UUID firstRequest = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        when(aiServiceClient.isRerankReady()).thenReturn(true);
        when(jdbc.queryForList(RerankPrefetchJob.OPEN_STUDYING_REQUESTS)).thenReturn(List.of(
                Map.of("source_id", firstRequest, "user_id", firstUser),
                Map.of("source_id", secondRequest, "user_id", secondUser)));
        UUID savedByStudent = UUID.randomUUID();
        UUID savedByTutor = UUID.randomUUID();
        when(jdbc.queryForList(RerankPrefetchJob.SAVED_SEARCHES, "STUDENT_TO_TUTOR"))
                .thenReturn(List.of(Map.of("source_id", savedByStudent, "user_id", firstUser)));
        when(jdbc.queryForList(RerankPrefetchJob.SAVED_SEARCHES, "TUTOR_TO_STUDENT"))
                .thenReturn(List.of(Map.of("source_id", savedByTutor, "user_id", secondUser)));
        when(recommendationService.prefetchRerank(firstUser, firstRequest))
                .thenThrow(new IllegalStateException("subject was removed"));

        job.run();

        verify(recommendationService).prefetchRerank(firstUser, firstRequest);
        verify(recommendationService).prefetchRerank(secondUser, secondRequest);
        verify(recommendationService).prefetchRerankForSavedSearch(firstUser, savedByStudent);
        verify(studentRecommendationService).prefetchRerankForSavedSearch(secondUser, savedByTutor);
        verify(rerankService).deleteOlderThan(any());
    }

    @Test
    void onlyCleansUpWhenTheAiServiceHasNoRerankerReady() {
        when(aiServiceClient.isRerankReady()).thenReturn(false);

        job.run();

        verify(rerankService).deleteOlderThan(any());
        verify(recommendationService, never()).prefetchRerank(any(), any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void doesNothingWhenRerankingIsSwitchedOff() {
        ReflectionTestUtils.setField(settings, "rerankEnabled", false);

        job.run();

        verifyNoInteractions(jdbc, aiServiceClient, recommendationService, studentRecommendationService, rerankService);
    }
}
