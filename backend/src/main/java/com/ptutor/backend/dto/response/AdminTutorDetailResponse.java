package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;

import tools.jackson.databind.JsonNode;

public record AdminTutorDetailResponse(
        UUID userId,
        UUID tutorId,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        String avatarUrl,
        AddressResponse address,
        String introduction,
        Integer experienceYears,
        String education,
        String teachingStyleTags,
        String teachingMethodology,
        String strengthSubjects,
        String targetStudentType,
        BigDecimal averageRating,
        Integer totalReviews,
        Integer completedContractsCount,
        Integer totalStudentsTaught,
        BigDecimal acceptanceRate,
        BigDecimal avgResponseTimeHours,
        UserStatus accountStatus,
        TutorProfileStatus profileStatus,
        UUID profileReviewedByEmployeeId,
        String profileReviewedByName,
        LocalDateTime profileReviewedAt,
        String profileRejectionReason,
        BigDecimal recommendationScore,
        String scoreFormulaVersion,
        LocalDateTime scoreUpdatedAt,
        JsonNode scoreBreakdown,
        SuspensionType suspensionType,
        String suspensionReason,
        LocalDateTime suspendedAt,
        LocalDateTime suspendedUntil,
        int suspensionCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
