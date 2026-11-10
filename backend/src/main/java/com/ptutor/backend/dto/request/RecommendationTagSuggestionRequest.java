package com.ptutor.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecommendationTagSuggestionRequest(
        @NotNull(message = "Subject is required") UUID subjectId,
        @Min(value = 1, message = "maxTags must be between 1 and 20")
        @Max(value = 20, message = "maxTags must be between 1 and 20") Integer maxTags) {
}
