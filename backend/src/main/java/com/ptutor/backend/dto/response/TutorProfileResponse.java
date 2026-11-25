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

    public TutorProfileResponse withAvatarUrl(String value) {
        return new TutorProfileResponse(tutorId, userId, firstName, lastName, value, introduction, experienceYears,
                education, teachingStyleTags, teachingMethodology, strengthSubjects, targetStudentType, averageRating,
                totalReviews, completedContractsCount, totalStudentsTaught, acceptanceRate, avgResponseTimeHours,
                createdAt, updatedAt);
    }
}
