package com.ptutor.backend.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.MatchAlertService;
import com.ptutor.backend.service.RecommendationSettings;

/**
 * Runs the MATCH_ALERT jobs (design 07, 7.3). A job is queued whenever a live posting gets its
 * vector - when it is published and when it is edited - so the comparison always uses fresh data
 * and happens in the background, never while someone is waiting for search results.
 */
@Component
public class MatchAlertWorker {

    private static final Logger log = LoggerFactory.getLogger(MatchAlertWorker.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiEnrichmentJobRepository jobRepository;
    private final MatchAlertService matchAlertService;
    private final RecommendationSettings settings;
    private final TransactionTemplate transaction;
    private final int batchSize;
    private final int maxAttempts;

    public MatchAlertWorker(
            AiEnrichmentJobRepository jobRepository,
            MatchAlertService matchAlertService,
            RecommendationSettings settings,
            PlatformTransactionManager transactionManager,
            @Value("${app.recommendation.proactive.worker-batch-size:5}") int batchSize,
            @Value("${app.recommendation.proactive.max-attempts:3}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.matchAlertService = matchAlertService;
        this.settings = settings;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(
            initialDelayString = "${app.recommendation.proactive.worker-initial-delay-ms:40000}",
            fixedDelayString = "${app.recommendation.proactive.worker-interval-ms:30000}")
    public void run() {
        if (!settings.isAlertEnabled()) {
            return;
        }
        try {
            if (!jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                    AiJobType.MATCH_ALERT, AiJobStatus.PENDING, LocalDateTime.now())) {
                return;
            }
            List<ClaimedJob> jobs = claim();
            int notified = 0;
            for (ClaimedJob job : jobs) {
                notified += process(job);
            }
            if (!jobs.isEmpty()) {
                log.info("Match alert worker checked {} postings and sent {} notifications", jobs.size(), notified);
            }
        } catch (Exception exception) {
            log.warn("Match alert worker run failed", exception);
        }
    }

    List<ClaimedJob> claim() {
        return transaction.execute(status -> {
            List<AiEnrichmentJob> jobs = jobRepository.lockDueJobs(
                    AiJobType.MATCH_ALERT.name(), LocalDateTime.now(), batchSize);
            jobs.forEach(job -> {
                job.setStatus(AiJobStatus.PROCESSING);
                job.setAttempts(job.getAttempts() + 1);
            });
            return jobs.stream()
                    .map(job -> new ClaimedJob(job.getId(), job.getEntityType(), job.getEntityId(), job.getAttempts()))
                    .toList();
        });
    }

    /** @return how many people were notified about the posting of this job */
    int process(ClaimedJob job) {
        try {
            int notified = matchAlertService.process(job.entityType(), job.entityId());
            transaction.executeWithoutResult(status -> jobRepository.finish(
                    job.id(), AiJobStatus.DONE, null, notified + " notified", LocalDateTime.now()));
            return notified;
        } catch (Exception exception) {
            log.warn("Match alert job {} for {} {} failed", job.id(), job.entityType(), job.entityId(), exception);
            String message = exception.toString();
            String error = message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
            LocalDateTime now = LocalDateTime.now();
            if (job.attempts() >= maxAttempts) {
                transaction.executeWithoutResult(
                        status -> jobRepository.finish(job.id(), AiJobStatus.FAILED, null, error, now));
            } else {
                transaction.executeWithoutResult(status -> jobRepository.retryLater(
                        job.id(), now.plus(Duration.ofMinutes(5L * job.attempts())), error, now));
            }
            return 0;
        }
    }
}
