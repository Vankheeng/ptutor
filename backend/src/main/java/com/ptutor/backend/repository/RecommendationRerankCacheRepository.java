package com.ptutor.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.RecommendationRerankCacheId;

public interface RecommendationRerankCacheRepository extends JpaRepository<RecommendationRerankCache, RecommendationRerankCacheId> {
}
