package com.ptutor.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationExposureStats;
import com.ptutor.backend.entity.RecommendationExposureStatsId;

public interface RecommendationExposureStatsRepository extends JpaRepository<RecommendationExposureStats, RecommendationExposureStatsId> {
}
