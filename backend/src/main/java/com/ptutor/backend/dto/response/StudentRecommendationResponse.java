package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;

import tools.jackson.databind.JsonNode;

public record StudentRecommendationResponse(
        UUID logId,
        String algorithmVersion,
        JsonNode queryContext,
        /** Required criteria still missing (subjectId, gradeIds); when not empty there are no items. */
        List<String> needsInput,
        boolean lowConfidence,
        List<Item> items,
        List<RelaxationSuggestion> relaxations,
        int page,
        int size,
        long totalElements) {

    public record Item(
            int rank,
            UUID studyingRequestId,
            Student student,
            String title,
            String learningGoals,
            PriceRange priceRange,
            LearningMode learningMode,
            double score,
            boolean isExploration,
            List<String> reasons,
            String caution) {
    }

    /** Deliberately limited: no name, photo, phone number or detailed address of the student. */
    public record Student(String displayName, String district) {
    }

    public record PriceRange(BigDecimal min, BigDecimal max) {
    }
}
