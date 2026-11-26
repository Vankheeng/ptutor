package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.WeightLearningMode;

import jakarta.validation.constraints.NotNull;

/** The mode an administrator sets for a learned weight model. */
public record AdminRecommendationWeightModeRequest(@NotNull(message = "Mode is required") WeightLearningMode mode) {
}
