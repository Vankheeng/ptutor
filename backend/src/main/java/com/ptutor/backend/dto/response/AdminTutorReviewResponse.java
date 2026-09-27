package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminTutorReviewResponse(
        UUID reviewId,
        UUID studentUserId,
        String studentName,
        String studentEmail,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
