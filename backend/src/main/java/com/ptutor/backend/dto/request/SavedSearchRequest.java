package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.RecommendationDirection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * @param filters the filters of a search in that direction: the same object as "filters" of
 *                the tutor search (STUDENT_TO_TUTOR) or of the student search (TUTOR_TO_STUDENT)
 */
public record SavedSearchRequest(
        @NotNull(message = "Direction is required") RecommendationDirection direction,
        @Size(max = 120, message = "Name must not exceed 120 characters") String name,
        @NotNull(message = "Filters are required") JsonNode filters,
        @Size(max = 500, message = "Semantic text must not exceed 500 characters") String semanticText,
        Boolean notifyEnabled) {
}
