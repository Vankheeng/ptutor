package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.RecommendationDirection;

import tools.jackson.databind.JsonNode;

public record SavedSearchResponse(
        UUID id,
        RecommendationDirection direction,
        String name,
        JsonNode filters,
        String semanticText,
        boolean notifyEnabled,
        /** When the user was last told about a posting that fits this search; null when never. */
        LocalDateTime lastNotifiedAt,
        LocalDateTime createdAt) {
}
