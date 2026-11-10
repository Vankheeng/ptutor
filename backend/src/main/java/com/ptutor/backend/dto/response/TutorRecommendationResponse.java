package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.TeachingMode;

import tools.jackson.databind.JsonNode;

public record TutorRecommendationResponse(
        UUID logId,
        String algorithmVersion,
        /** Final criteria after merging, with the source of each one. */
        JsonNode queryContext,
        /** Required criteria still missing (subjectId, gradeId); when not empty there are no items. */
        List<String> needsInput,
        boolean lowConfidence,
        List<Item> items,
        /** Ways to loosen the search that would bring more good results; empty when there are enough. */
        List<RelaxationSuggestion> relaxations,
        int page,
        int size,
        long totalElements) {

    public record Item(
            int rank,
            UUID teachingRequestId,
            Tutor tutor,
            String title,
            BigDecimal expectedPrice,
            TeachingMode teachingMode,
            List<String> districts,
            double score,
            boolean isExploration,
            List<String> reasons,
            String caution) {
    }

    public record Tutor(
            UUID userId,
            String displayName,
            String avatarUrl,
            BigDecimal averageRating,
            Integer totalReviews) {
    }
}
