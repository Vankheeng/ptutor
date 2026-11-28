package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TutorReviewOverviewResponse(
        BigDecimal averageRating,
        long totalReviews,
        long commentCount,
        List<RatingDistribution> distribution,
        PageResponse<PublicReviewResponse> comments) {

    public record RatingDistribution(
            int rating,
            long count,
            BigDecimal percentage) {
    }
}
