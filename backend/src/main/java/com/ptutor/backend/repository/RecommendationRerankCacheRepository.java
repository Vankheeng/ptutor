package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.RecommendationRerankCacheId;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;

public interface RecommendationRerankCacheRepository extends JpaRepository<RecommendationRerankCache, RecommendationRerankCacheId> {

    @Query("""
            select cache from RecommendationRerankCache cache
            where cache.id.queryHash = :queryHash and cache.id.candidateType = :candidateType
              and cache.id.modelVersion = :modelVersion and cache.id.candidateId in :candidateIds
            """)
    List<RecommendationRerankCache> findScores(
            @Param("queryHash") String queryHash,
            @Param("candidateType") RecommendationCandidateType candidateType,
            @Param("modelVersion") String modelVersion,
            @Param("candidateIds") Collection<UUID> candidateIds);

    /** Two searches may score the same pair at the same moment; the later write simply wins. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO recommendation_rerank_cache
                (query_hash, candidate_type, candidate_id, candidate_content_hash, model_version, rerank_score, computed_at)
            VALUES (:queryHash, :candidateType, :candidateId, :contentHash, :modelVersion, :score, :now)
            ON CONFLICT (query_hash, candidate_type, candidate_id, model_version)
            DO UPDATE SET candidate_content_hash = EXCLUDED.candidate_content_hash,
                          rerank_score = EXCLUDED.rerank_score, computed_at = EXCLUDED.computed_at
            """, nativeQuery = true)
    void upsert(
            @Param("queryHash") String queryHash,
            @Param("candidateType") String candidateType,
            @Param("candidateId") UUID candidateId,
            @Param("contentHash") String contentHash,
            @Param("modelVersion") String modelVersion,
            @Param("score") BigDecimal score,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    int deleteByComputedAtBefore(LocalDateTime threshold);
}
