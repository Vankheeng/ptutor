package com.ptutor.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;
import com.ptutor.backend.service.EnrichmentJobProcessor;

@ExtendWith(MockitoExtension.class)
class EnrichmentWorkerTest {

    @Mock EnrichmentJobProcessor processor;
    @Mock AiServiceClient aiServiceClient;

    @Test
    void processesEveryClaimedJobWhenTheLlmIsAvailable() {
        ClaimedJob first = job();
        ClaimedJob second = job();
        when(processor.hasDueJobs()).thenReturn(true);
        when(aiServiceClient.isLlmReady()).thenReturn(true);
        when(processor.claim(3)).thenReturn(List.of(first, second));
        when(processor.process(any())).thenReturn(Outcome.DONE);

        worker(true).run();

        verify(processor).process(first);
        verify(processor).process(second);
    }

    @Test
    void leavesJobsQueuedWithoutConsumingAttemptsWhenThereIsNoLlm() {
        when(processor.hasDueJobs()).thenReturn(true);
        when(aiServiceClient.isLlmReady()).thenReturn(false);

        worker(true).run();

        verify(processor, never()).claim(anyInt());
        verify(processor, never()).process(any());
    }

    @Test
    void doesNotAskTheAiServiceWhenTheQueueIsEmpty() {
        when(processor.hasDueJobs()).thenReturn(false);

        worker(true).run();

        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void doesNothingWhenDisabledAndNeverLetsAFailureEscape() {
        worker(false).run();
        verifyNoInteractions(processor, aiServiceClient);

        when(processor.hasDueJobs()).thenThrow(new IllegalStateException("database down"));
        assertThatCode(() -> worker(true).run()).doesNotThrowAnyException();
    }

    private EnrichmentWorker worker(boolean enabled) {
        return new EnrichmentWorker(processor, aiServiceClient, enabled, 3);
    }

    private ClaimedJob job() {
        return new ClaimedJob(UUID.randomUUID(), RecommendationEntityType.TUTOR, UUID.randomUUID(), 1);
    }
}
