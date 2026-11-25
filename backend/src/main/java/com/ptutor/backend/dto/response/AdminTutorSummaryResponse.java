package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;

public record AdminTutorSummaryResponse(
        UUID userId,
        UUID tutorId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String avatarUrl,
        UserStatus accountStatus,
        TutorProfileStatus profileStatus,
        BigDecimal averageRating,
        Integer totalReviews,
        BigDecimal recommendationScore,
        int suspensionCount,
        LocalDateTime scoreUpdatedAt,
        LocalDateTime createdAt) {
}
