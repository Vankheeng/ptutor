package com.ptutor.backend.tutor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.Tutor;

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

    public static TutorProfileResponse from(Tutor tutor) {
        return new TutorProfileResponse(
                tutor.getId(),
                tutor.getUser().getId(),
                tutor.getUser().getFirstName(),
                tutor.getUser().getLastName(),
                tutor.getUser().getAvatarUrl(),
                tutor.getIntroduction(),
                tutor.getExperienceYears(),
                tutor.getEducation(),
                tutor.getTeachingStyleTags(),
                tutor.getTeachingMethodology(),
                tutor.getStrengthSubjects(),
                tutor.getTargetStudentType(),
                tutor.getAverageRating(),
                tutor.getTotalReviews(),
                tutor.getCompletedContractsCount(),
                tutor.getTotalStudentsTaught(),
                tutor.getAcceptanceRate(),
                tutor.getAvgResponseTimeHours(),
                tutor.getCreatedAt(),
                tutor.getUpdatedAt());
    }
}
