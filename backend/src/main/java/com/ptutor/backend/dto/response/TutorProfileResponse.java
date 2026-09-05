package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TutorProfileResponse(
        UUID tutorId,
        UUID userId,
        String firstName,
        String lastName,
        String avatarUrl,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
