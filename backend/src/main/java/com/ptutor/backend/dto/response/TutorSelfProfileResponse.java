package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.Gender;

public record TutorSelfProfileResponse(
        UUID tutorId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phone,
        Gender gender,
        LocalDate dateOfBirth,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
