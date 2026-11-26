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
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.PreferenceProfileService;
import com.ptutor.backend.service.RecommendationSettings;

/**
 * Keeps the preference profiles (H1) up to date. Every recorded interaction queues one
 * PROFILE_UPDATE job for the person; several interactions in a row collapse into one job, so the
 * profile is rebuilt once. Every night all profiles are queued again so that old behaviour fades.
 */
@Component
public class ProfileUpdateWorker {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpdateWorker.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiEnrichmentJobRepository jobRepository;
    private final PreferenceProfileService profileService;
    private final RecommendationSettings settings;
    private final TransactionTemplate transaction;
    private final int batchSize;
    private final int maxAttempts;

    public ProfileUpdateWorker(
            AiEnrichmentJobRepository jobRepository,
            PreferenceProfileService profileService,
            RecommendationSettings settings,
            PlatformTransactionManager transactionManager,
            @Value("${app.recommendation.history.worker-batch-size:20}") int batchSize,
            @Value("${app.recommendation.history.max-attempts:5}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.profileService = profileService;
        this.settings = settings;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(
            initialDelayString = "${app.recommendation.history.worker-initial-delay-ms:25000}",
            fixedDelayString = "${app.recommendation.history.worker-interval-ms:10000}")
    public void run() {
        if (!settings.isHistoryEnabled()) {
            return;
        }
        try {
            if (!jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                    AiJobType.PROFILE_UPDATE, AiJobStatus.PENDING, LocalDateTime.now())) {
                return;
            }
            List<ClaimedJob> jobs = claim();
            int failed = 0;
            for (ClaimedJob job : jobs) {
                failed += process(job) ? 0 : 1;
            }
            if (!jobs.isEmpty()) {
                log.info("Profile worker rebuilt {} preference profiles ({} failed)", jobs.size() - failed, failed);
            }
        } catch (Exception exception) {
            log.warn("Profile worker run failed", exception);
        }
    }

    /** 19:30 UTC is 02:30 in Vietnam. */
    @Scheduled(cron = "${app.recommendation.history.nightly-cron:0 30 19 * * *}", zone = "UTC")
    public void queueAll() {
        if (!settings.isHistoryEnabled()) {
            return;
        }
        Integer queued = transaction.execute(status -> jobRepository.enqueueProfileUpdates(LocalDateTime.now()));
        log.info("Queued {} preference profiles for their nightly rebuild", queued);
    }

    List<ClaimedJob> claim() {
        return transaction.execute(status -> {
            List<AiEnrichmentJob> jobs = jobRepository.lockDueJobs(
                    AiJobType.PROFILE_UPDATE.name(), LocalDateTime.now(), batchSize);
            jobs.forEach(job -> {
                job.setStatus(AiJobStatus.PROCESSING);
                job.setAttempts(job.getAttempts() + 1);
            });
            return jobs.stream()
                    .map(job -> new ClaimedJob(job.getId(), job.getEntityType(), job.getEntityId(), job.getAttempts()))
                    .toList();
        });
    }

    boolean process(ClaimedJob job) {
        try {
            RecommendationProfileRole role = job.entityType() == RecommendationEntityType.TUTOR
                    ? RecommendationProfileRole.TUTOR : RecommendationProfileRole.STUDENT;
            boolean exists = profileService.recompute(role, job.entityId());
            transaction.executeWithoutResult(status -> jobRepository.finish(
                    job.id(), exists ? AiJobStatus.DONE : AiJobStatus.SKIPPED, null,
                    exists ? null : "Person no longer exists", LocalDateTime.now()));
            return true;
        } catch (Exception exception) {
            log.warn("Profile job {} for {} {} failed", job.id(), job.entityType(), job.entityId(), exception);
            String message = exception.toString();
            String error = message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
            LocalDateTime now = LocalDateTime.now();
            if (job.attempts() >= maxAttempts) {
                transaction.executeWithoutResult(
                        status -> jobRepository.finish(job.id(), AiJobStatus.FAILED, null, error, now));
            } else {
                Duration delay = Duration.ofMinutes(Math.min(60, 5L * job.attempts()));
                transaction.executeWithoutResult(
                        status -> jobRepository.retryLater(job.id(), now.plus(delay), error, now));
            }
            return false;
        }
    }
}
