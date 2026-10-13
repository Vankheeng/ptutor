package com.ptutor.backend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.service.RecommendationService;
import com.ptutor.backend.service.RecommendationSettings;
import com.ptutor.backend.service.RerankService;
import com.ptutor.backend.service.StudentRecommendationService;

import lombok.RequiredArgsConstructor;

/**
 * Every night, scores ahead of time the best results for each OPEN studying posting and for each
 * saved search (design 07, 7.3), so that a user who searches from them gets reranked results
 * without waiting for the model.
 */
@Component
@RequiredArgsConstructor
public class RerankPrefetchJob {

    private static final Logger log = LoggerFactory.getLogger(RerankPrefetchJob.class);

    static final String OPEN_STUDYING_REQUESTS = """
            SELECT sr.id AS source_id, s.user_id AS user_id
            FROM studying_requests sr JOIN students s ON s.id = sr.student_id
            WHERE sr.deleted_at IS NULL AND sr.status = 'OPEN'
            ORDER BY sr.created_at DESC
            """;

    static final String SAVED_SEARCHES = """
            SELECT ss.id AS source_id, ss.user_id AS user_id
            FROM recommendation_saved_searches ss
            WHERE ss.deleted_at IS NULL AND ss.direction = ?
            ORDER BY ss.created_at DESC
            """;

    private final JdbcTemplate jdbc;
    private final AiServiceClient aiServiceClient;
    private final RecommendationService recommendationService;
    private final StudentRecommendationService studentRecommendationService;
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
        int[] totals = new int[3];
        prefetch(jdbc.queryForList(OPEN_STUDYING_REQUESTS), "studying request",
                recommendationService::prefetchRerank, totals);
        prefetch(jdbc.queryForList(SAVED_SEARCHES, "STUDENT_TO_TUTOR"), "saved search",
                recommendationService::prefetchRerankForSavedSearch, totals);
        prefetch(jdbc.queryForList(SAVED_SEARCHES, "TUTOR_TO_STUDENT"), "saved search",
                studentRecommendationService::prefetchRerankForSavedSearch, totals);
        log.info("Rerank prefetch: {} sources, {} pairs scored, {} failed, {} old cache rows deleted",
                totals[0], totals[1], totals[2], deleted);
    }

    /** @param totals sources, pairs scored, failures */
    private void prefetch(List<Map<String, Object>> sources, String kind,
            BiFunction<UUID, UUID, Integer> prefetch, int[] totals) {
        for (Map<String, Object> source : sources) {
            totals[0]++;
            try {
                totals[1] += prefetch.apply((UUID) source.get("user_id"), (UUID) source.get("source_id"));
            } catch (RuntimeException exception) {
                // One source that cannot be searched from must not stop the others.
                totals[2]++;
                log.warn("Rerank prefetch failed for {} {}: {}", kind, source.get("source_id"), exception.toString());
            }
        }
    }
}
