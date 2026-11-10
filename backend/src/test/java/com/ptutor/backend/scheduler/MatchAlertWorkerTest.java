package com.ptutor.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.MatchAlertService;
import com.ptutor.backend.service.RecommendationSettings;

class MatchAlertWorkerTest {

    private final AiEnrichmentJobRepository jobRepository = mock(AiEnrichmentJobRepository.class);
    private final MatchAlertService matchAlertService = mock(MatchAlertService.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final MatchAlertWorker worker;

    MatchAlertWorkerTest() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new MatchAlertWorker(jobRepository, matchAlertService, settings, transactionManager, 5, 2);
    }

    @Test
    void checksEveryQueuedPostingAndRecordsHowManyPeopleWereTold() {
        UUID postingId = UUID.randomUUID();
        AiEnrichmentJob job = new AiEnrichmentJob();
        job.setId(UUID.randomUUID());
        job.setJobType(AiJobType.MATCH_ALERT);
        job.setEntityType(RecommendationEntityType.TEACHING_REQUEST);
        job.setEntityId(postingId);
        job.setStatus(AiJobStatus.PENDING);
        job.setAttempts(0);
        when(jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                eq(AiJobType.MATCH_ALERT), eq(AiJobStatus.PENDING), any())).thenReturn(true);
        when(jobRepository.lockDueJobs(eq("MATCH_ALERT"), any(), anyInt())).thenReturn(List.of(job));
        when(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, postingId)).thenReturn(2);

        worker.run();

        verify(matchAlertService).process(RecommendationEntityType.TEACHING_REQUEST, postingId);
        verify(jobRepository).finish(eq(job.getId()), eq(AiJobStatus.DONE), isNull(), eq("2 notified"), any());
    }

    @Test
    void retriesAFailedCheckLaterAndGivesUpAfterTheLastAttempt() {
        UUID id = UUID.randomUUID();
        when(matchAlertService.process(any(), any())).thenThrow(new IllegalStateException("database hiccup"));

        assertThat(worker.process(new ClaimedJob(id, RecommendationEntityType.STUDYING_REQUEST, UUID.randomUUID(), 1)))
                .isZero();
        verify(jobRepository).retryLater(eq(id), any(), any(), any());

        worker.process(new ClaimedJob(id, RecommendationEntityType.STUDYING_REQUEST, UUID.randomUUID(), 2));
        verify(jobRepository).finish(eq(id), eq(AiJobStatus.FAILED), isNull(), any(), any());
    }

    @Test
    void doesNothingWithoutDueJobsOrWhenAlertsAreSwitchedOff() {
        worker.run();
        verify(jobRepository, never()).lockDueJobs(any(), any(), anyInt());

        ReflectionTestUtils.setField(settings, "alertEnabled", false);
        worker.run();
        verifyNoInteractions(matchAlertService);
    }
}
