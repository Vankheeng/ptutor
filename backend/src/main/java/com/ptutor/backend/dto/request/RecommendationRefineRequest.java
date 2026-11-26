package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param seenUpToRank the last rank already on the user's screen; results up to it are never reordered */
public record RecommendationRefineRequest(
        @NotNull(message = "seenUpToRank is required")
        @Min(value = 0, message = "seenUpToRank must not be negative")
        @Max(value = 50, message = "seenUpToRank must not exceed 50")
        Integer seenUpToRank) {
}
