package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicReviewResponse(
        String displayName,
        UUID tutorId,
        String tutorName,
        String tutorAvatarUrl,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
