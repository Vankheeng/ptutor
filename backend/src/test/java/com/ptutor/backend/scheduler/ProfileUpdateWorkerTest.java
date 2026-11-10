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
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.PreferenceProfileService;
import com.ptutor.backend.service.RecommendationSettings;

class ProfileUpdateWorkerTest {

    private final AiEnrichmentJobRepository jobRepository = mock(AiEnrichmentJobRepository.class);
    private final PreferenceProfileService profileService = mock(PreferenceProfileService.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ProfileUpdateWorker worker;

    ProfileUpdateWorkerTest() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new ProfileUpdateWorker(jobRepository, profileService, settings, transactionManager, 20, 3);
    }

    @Test
    void rebuildsTheProfileOfEveryQueuedPersonWithTheirRole() {
        UUID studentId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        AiEnrichmentJob studentJob = job(RecommendationEntityType.STUDENT, studentId);
        AiEnrichmentJob tutorJob = job(RecommendationEntityType.TUTOR, tutorId);
        when(jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                eq(AiJobType.PROFILE_UPDATE), eq(AiJobStatus.PENDING), any())).thenReturn(true);
        when(jobRepository.lockDueJobs(eq("PROFILE_UPDATE"), any(), anyInt())).thenReturn(List.of(studentJob, tutorJob));
        when(profileService.recompute(RecommendationProfileRole.STUDENT, studentId)).thenReturn(true);
        when(profileService.recompute(RecommendationProfileRole.TUTOR, tutorId)).thenReturn(false);

        worker.run();

        verify(profileService).recompute(RecommendationProfileRole.STUDENT, studentId);
        verify(profileService).recompute(RecommendationProfileRole.TUTOR, tutorId);
        verify(jobRepository).finish(eq(studentJob.getId()), eq(AiJobStatus.DONE), isNull(), isNull(), any());
        verify(jobRepository).finish(eq(tutorJob.getId()), eq(AiJobStatus.SKIPPED), isNull(),
                eq("Person no longer exists"), any());
        assertThat(studentJob.getStatus()).isEqualTo(AiJobStatus.PROCESSING);
        assertThat(studentJob.getAttempts()).isEqualTo(1);
    }

    @Test
    void retriesAFailedRebuildLaterAndGivesUpAfterTheLastAttempt() {
        UUID id = UUID.randomUUID();
        when(profileService.recompute(any(), any())).thenThrow(new IllegalStateException("database hiccup"));

        assertThat(worker.process(new ClaimedJob(id, RecommendationEntityType.STUDENT, UUID.randomUUID(), 1))).isFalse();
        verify(jobRepository).retryLater(eq(id), any(), any(), any());

        assertThat(worker.process(new ClaimedJob(id, RecommendationEntityType.STUDENT, UUID.randomUUID(), 3))).isFalse();
        verify(jobRepository).finish(eq(id), eq(AiJobStatus.FAILED), isNull(), any(), any());
    }

    @Test
    void doesNothingWithoutDueJobsOrWhenHistoryIsSwitchedOff() {
        worker.run();
        verify(jobRepository, never()).lockDueJobs(any(), any(), anyInt());

        ReflectionTestUtils.setField(settings, "historyEnabled", false);
        worker.run();
        worker.queueAll();
        verifyNoInteractions(profileService);
        verify(jobRepository, never()).enqueueProfileUpdates(any());
    }

    @Test
    void queuesEveryProfileForTheNightlyRebuild() {
        when(jobRepository.enqueueProfileUpdates(any())).thenReturn(7);

        worker.queueAll();

        verify(jobRepository).enqueueProfileUpdates(any());
    }

    private AiEnrichmentJob job(RecommendationEntityType entityType, UUID entityId) {
        AiEnrichmentJob job = new AiEnrichmentJob();
        job.setId(UUID.randomUUID());
        job.setJobType(AiJobType.PROFILE_UPDATE);
        job.setEntityType(entityType);
        job.setEntityId(entityId);
        job.setStatus(AiJobStatus.PENDING);
        job.setAttempts(0);
        return job;
    }
}
