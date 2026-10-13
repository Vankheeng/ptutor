package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.RecommendationExposureStats;
import com.ptutor.backend.entity.RecommendationExposureStatsId;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;

public interface RecommendationExposureStatsRepository extends JpaRepository<RecommendationExposureStats, RecommendationExposureStatsId> {

    @Query("""
            select stats from RecommendationExposureStats stats
            where stats.id.candidateType = :candidateType and stats.id.candidateId in :candidateIds
            """)
    List<RecommendationExposureStats> findAllOf(
            @Param("candidateType") RecommendationCandidateType candidateType,
            @Param("candidateIds") Collection<UUID> candidateIds);

    /** Postings that were not shown in the last week must not keep last week's numbers. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE recommendation_exposure_stats SET impressions_7d = 0, clicks_7d = 0, updated_at = :now",
            nativeQuery = true)
    int clearWeek(@Param("now") LocalDateTime now);

    /**
     * Counts everything again from the logs. An impression is a posting on the first page of a search
     * (rank up to {@code pageSize}: that page is always sent to the user). A click is one search in
     * which the user opened, liked or contacted the posting.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO recommendation_exposure_stats
                (candidate_type, candidate_id, impressions_7d, clicks_7d, impressions_total, clicks_total, updated_at)
            SELECT counted.candidate_type, counted.candidate_id,
                   SUM(counted.impression_7d), SUM(counted.click_7d), SUM(counted.impression), SUM(counted.click), :now
            FROM (
                SELECT CASE WHEN l.direction = 'STUDENT_TO_TUTOR' THEN 'TEACHING_REQUEST' ELSE 'STUDYING_REQUEST' END
                           AS candidate_type,
                       CAST(candidate ->> 'candidateId' AS uuid) AS candidate_id,
                       CASE WHEN l.created_at >= :weekAgo THEN 1 ELSE 0 END AS impression_7d, 0 AS click_7d,
                       1 AS impression, 0 AS click
                FROM recommendation_logs l
                CROSS JOIN LATERAL jsonb_array_elements(l.returned_candidate_ids) AS candidate
                WHERE l.deleted_at IS NULL AND l.direction IS NOT NULL
                  AND jsonb_typeof(l.returned_candidate_ids) = 'array'
                  AND CAST(candidate ->> 'rank' AS integer) <= :pageSize
                UNION ALL
                SELECT clicked.candidate_type, clicked.candidate_id,
                       0, CASE WHEN clicked.clicked_at >= :weekAgo THEN 1 ELSE 0 END, 0, 1
                FROM (
                    SELECT CASE WHEN cl.candidate_teaching_request_id IS NOT NULL
                                THEN 'TEACHING_REQUEST' ELSE 'STUDYING_REQUEST' END AS candidate_type,
                           COALESCE(cl.candidate_teaching_request_id, cl.candidate_studying_request_id) AS candidate_id,
                           MAX(cl.created_at) AS clicked_at
                    FROM recommendation_click_logs cl
                    WHERE cl.deleted_at IS NULL AND cl.action <> 'DISMISSED'
                      AND COALESCE(cl.candidate_teaching_request_id, cl.candidate_studying_request_id) IS NOT NULL
                    GROUP BY cl.recommendation_log_id, 1, 2
                ) clicked
            ) counted
            GROUP BY counted.candidate_type, counted.candidate_id
            ON CONFLICT (candidate_type, candidate_id) DO UPDATE SET
                impressions_7d = EXCLUDED.impressions_7d, clicks_7d = EXCLUDED.clicks_7d,
                impressions_total = EXCLUDED.impressions_total, clicks_total = EXCLUDED.clicks_total,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int recount(@Param("weekAgo") LocalDateTime weekAgo, @Param("pageSize") int pageSize, @Param("now") LocalDateTime now);
}
