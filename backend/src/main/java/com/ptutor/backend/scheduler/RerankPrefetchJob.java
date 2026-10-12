package com.ptutor.backend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.service.RecommendationService;
import com.ptutor.backend.service.RecommendationSettings;
import com.ptutor.backend.service.RerankService;

import lombok.RequiredArgsConstructor;

/**
 * Every night, scores the best tutors for each OPEN studying posting ahead of time (design 07, 7.3),
 * so that a student who searches from their posting gets reranked results without waiting for the
 * model. Saved searches join this job when they exist.
 */
@Component
@RequiredArgsConstructor
public class RerankPrefetchJob {

    private static final Logger log = LoggerFactory.getLogger(RerankPrefetchJob.class);

    static final String OPEN_STUDYING_REQUESTS = """
            SELECT sr.id AS request_id, s.user_id AS user_id
            FROM studying_requests sr JOIN students s ON s.id = sr.student_id
            WHERE sr.deleted_at IS NULL AND sr.status = 'OPEN'
            ORDER BY sr.created_at DESC
            """;

    private final JdbcTemplate jdbc;
    private final AiServiceClient aiServiceClient;
    private final RecommendationService recommendationService;
    private final RerankService rerankService;
    private final RecommendationSettings settings;

    /** 19:00 UTC is 02:00 in Vietnam. */
    @Scheduled(cron = "${app.recommendation.rerank.prefetch-cron:0 0 19 * * *}", zone = "UTC")
    public void run() {
        if (!settings.isRerankEnabled()) {
            return;
        }
        int deleted = rerankService.deleteOlderThan(
                LocalDateTime.now().minusDays(settings.getRerankCacheRetentionDays()));
        if (!aiServiceClient.isRerankReady()) {
            log.info("Rerank prefetch skipped: the AI service has no reranking model ready");
            return;
        }
        List<Map<String, Object>> requests = jdbc.queryForList(OPEN_STUDYING_REQUESTS);
        int computed = 0;
        int failed = 0;
        for (Map<String, Object> request : requests) {
            try {
                computed += recommendationService.prefetchRerank(
                        (UUID) request.get("user_id"), (UUID) request.get("request_id"));
            } catch (RuntimeException exception) {
                // One posting that cannot be searched from must not stop the others.
                failed++;
                log.warn("Rerank prefetch failed for studying request {}: {}",
                        request.get("request_id"), exception.toString());
            }
        }
        log.info("Rerank prefetch: {} studying requests, {} pairs scored, {} failed, {} old cache rows deleted",
                requests.size(), computed, failed, deleted);
    }
}
