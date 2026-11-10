package com.ptutor.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.RecommendationLearningModeOverride;

@Repository
public interface RecommendationLearningModeOverrideRepository
        extends JpaRepository<RecommendationLearningModeOverride, WeightModel> {
}
