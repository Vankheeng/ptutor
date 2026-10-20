package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.RecommendationLearnedWeights;

@Repository
public interface RecommendationLearnedWeightsRepository extends JpaRepository<RecommendationLearnedWeights, Long> {

    Optional<RecommendationLearnedWeights> findByModelKeyAndActiveTrue(WeightModel modelKey);

    Optional<RecommendationLearnedWeights> findFirstByModelKeyOrderByVersionDesc(WeightModel modelKey);

    Optional<RecommendationLearnedWeights> findByModelKeyAndVersion(WeightModel modelKey, Integer version);

    List<RecommendationLearnedWeights> findAllByModelKeyOrderByVersionDesc(WeightModel modelKey);
}
