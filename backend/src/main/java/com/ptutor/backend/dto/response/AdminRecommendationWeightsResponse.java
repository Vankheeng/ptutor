package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.WeightLearningMode;

import tools.jackson.databind.JsonNode;

/** The learned ranking weights of every model, for administrators (design 08, 8.1; 12, 12.9). */
public record AdminRecommendationWeightsResponse(List<Model> models) {

    /**
     * @param prior          the hand-set weights, by feature
     * @param configuredMode the mode of the configuration
     * @param overrideMode   the mode an administrator set, null when the configuration decides
     * @param mode           the mode in force
     * @param activeVersion  null when no version is active: the prior is served
     * @param versions       newest first
     */
    public record Model(WeightModel model, List<String> features, Map<String, Double> prior,
            WeightLearningMode configuredMode, WeightLearningMode overrideMode, WeightLearningMode mode,
            Integer activeVersion, List<Version> versions) {
    }

    /** @param evaluation the comparison with the prior on held-out data, as the nightly job stored it */
    public record Version(int version, boolean active, int sampleCount, LocalDateTime trainedUntil,
            LocalDateTime createdAt, JsonNode weights, JsonNode evaluation) {
    }
}
