package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;

public record PublicReviewResponse(
        String displayName,
        String tutorName,
        String tutorAvatarUrl,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
