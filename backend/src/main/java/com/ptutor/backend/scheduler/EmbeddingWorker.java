package com.ptutor.backend.scheduler;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

/**
 * Drains EMBED jobs from the outbox. When the AI service is not ready the jobs simply wait:
 * nothing is claimed and no attempt is consumed.
 */
@Component
public class EmbeddingWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

    private final EmbeddingJobProcessor processor;
    private final AiServiceClient aiServiceClient;
    private final AiEnrichmentJobRepository jobRepository;
    private final boolean enabled;
    private final int batchSize;
    private final int retentionDays;

    public EmbeddingWorker(
            EmbeddingJobProcessor processor,
            AiServiceClient aiServiceClient,
            AiEnrichmentJobRepository jobRepository,
            @Value("${app.recommendation.embedding.enabled:true}") boolean enabled,
            @Value("${app.recommendation.embedding.batch-size:20}") int batchSize,
            @Value("${app.recommendation.jobs.retention-days:7}") int retentionDays) {
        this.processor = processor;
        this.aiServiceClient = aiServiceClient;
        this.jobRepository = jobRepository;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
    }

    @Scheduled(
            initialDelayString = "${app.recommendation.embedding.worker-initial-delay-ms:15000}",
            fixedDelayString = "${app.recommendation.embedding.worker-interval-ms:10000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            processor.releaseStaleJobs();
            if (!processor.hasDueJobs()) {
                return;
            }
            if (!aiServiceClient.isReady()) {
                log.debug("AI service is not ready; embedding jobs stay queued");
                return;
            }
            List<ClaimedJob> jobs = processor.claim(batchSize);
            Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
            for (ClaimedJob job : jobs) {
                outcomes.merge(processor.process(job), 1, Integer::sum);
            }
            if (!jobs.isEmpty()) {
                log.info("Embedding worker processed {} jobs: {}", jobs.size(), outcomes);
            }
        } catch (Exception exception) {
            log.warn("Embedding worker run failed", exception);
        }
    }

    /**
     * Queues entities that have no embedding yet: rows created before this feature, or whose job
     * was lost. Entities with nothing to embed are not queued again until they change.
     */
    @Scheduled(
            initialDelayString = "${app.recommendation.embedding.backfill-initial-delay-ms:120000}",
            fixedDelayString = "${app.recommendation.embedding.backfill-interval-ms:21600000}")
    @Transactional
    public void backfill() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int queued = jobRepository.backfillTeachingRequests(now)
                + jobRepository.backfillStudyingRequests(now)
                + jobRepository.backfillTutors(now)
                + jobRepository.backfillStudents(now);
        if (queued > 0) {
            log.info("Queued {} embedding jobs for entities without an embedding", queued);
        }
    }

    /** Removes old finished jobs and reports how many jobs are in each status. */
    @Scheduled(
            initialDelayString = "${app.recommendation.jobs.cleanup-initial-delay-ms:180000}",
            fixedDelayString = "${app.recommendation.jobs.cleanup-interval-ms:21600000}")
    @Transactional
    public void cleanUp() {
        if (!enabled) {
            return;
        }
        int deleted = jobRepository.deleteFinishedBefore(LocalDateTime.now().minusDays(retentionDays));
        Map<AiJobStatus, Long> counts = new EnumMap<>(AiJobStatus.class);
        jobRepository.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        if (counts.getOrDefault(AiJobStatus.FAILED, 0L) > 0) {
            log.warn("AI jobs by status: {} ({} old finished jobs deleted); FAILED jobs need attention",
                    counts, deleted);
        } else {
            log.info("AI jobs by status: {} ({} old finished jobs deleted)", counts, deleted);
        }
    }
}
