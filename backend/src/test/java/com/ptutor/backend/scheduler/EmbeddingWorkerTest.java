package com.ptutor.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

@ExtendWith(MockitoExtension.class)
class EmbeddingWorkerTest {

    @Mock EmbeddingJobProcessor processor;
    @Mock AiServiceClient aiServiceClient;
    @Mock AiEnrichmentJobRepository jobRepository;

    @Test
    void processesEveryClaimedJob() {
        ClaimedJob first = job();
        ClaimedJob second = job();
        when(processor.hasDueJobs()).thenReturn(true);
        when(aiServiceClient.isReady()).thenReturn(true);
        when(processor.claim(7)).thenReturn(List.of(first, second));
        when(processor.process(any())).thenReturn(Outcome.DONE);

        worker(true).run();

        verify(processor).releaseStaleJobs();
        verify(processor).process(first);
        verify(processor).process(second);
    }

    @Test
    void leavesJobsQueuedWhenAiServiceIsNotReady() {
        when(processor.hasDueJobs()).thenReturn(true);
        when(aiServiceClient.isReady()).thenReturn(false);

        worker(true).run();

        verify(processor, never()).claim(anyInt());
        verify(processor, never()).process(any());
    }

    @Test
    void doesNotCallAiServiceWhenQueueIsEmpty() {
        when(processor.hasDueJobs()).thenReturn(false);

        worker(true).run();

        verifyNoInteractions(aiServiceClient);
        verify(processor, never()).claim(anyInt());
    }

    @Test
    void doesNothingWhenDisabled() {
        worker(false).run();
        worker(false).backfill();
        worker(false).cleanUp();

        verifyNoInteractions(processor, aiServiceClient, jobRepository);
    }

    @Test
    void neverLetsAFailureEscapeTheScheduler() {
        when(processor.releaseStaleJobs()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> worker(true).run()).doesNotThrowAnyException();
    }

    @Test
    void backfillQueuesAllFourEntityKinds() {
        worker(true).backfill();

        verify(jobRepository).backfillTeachingRequests(any());
        verify(jobRepository).backfillStudyingRequests(any());
        verify(jobRepository).backfillTutors(any());
        verify(jobRepository).backfillStudents(any());
    }

    @Test
    void cleanUpDeletesFinishedJobsOlderThanRetentionAndCountsTheRest() {
        worker(true).cleanUp();

        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobRepository).deleteFinishedBefore(before.capture());
        assertThat(before.getValue()).isBetween(
                LocalDateTime.now().minusDays(7).minusMinutes(1), LocalDateTime.now().minusDays(7).plusMinutes(1));
        verify(jobRepository).countByStatus();
    }

    private EmbeddingWorker worker(boolean enabled) {
        return new EmbeddingWorker(processor, aiServiceClient, jobRepository, enabled, 7, 7);
    }

    private ClaimedJob job() {
        return new ClaimedJob(UUID.randomUUID(), RecommendationEntityType.TEACHING_REQUEST, UUID.randomUUID(), 1);
    }
}
