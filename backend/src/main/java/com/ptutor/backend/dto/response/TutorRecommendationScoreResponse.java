package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record TutorRecommendationScoreResponse(
        UUID userId,
        UUID tutorId,
        BigDecimal recommendationScore,
        String formulaVersion,
        LocalDateTime updatedAt,
        JsonNode breakdown) {
}
