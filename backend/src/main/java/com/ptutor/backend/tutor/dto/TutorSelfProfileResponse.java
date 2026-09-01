package com.ptutor.backend.tutor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.dto.response.AddressResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Tutor;
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

    public static TutorSelfProfileResponse from(Tutor tutor) {
        District district = tutor.getUser().getDistrict();
        return new TutorSelfProfileResponse(
                tutor.getId(),
                tutor.getUser().getId(),
                tutor.getUser().getEmail(),
                tutor.getUser().getFirstName(),
                tutor.getUser().getLastName(),
                tutor.getUser().getPhone(),
                tutor.getUser().getGender(),
                tutor.getUser().getDateOfBirth(),
                tutor.getUser().getAvatarUrl(),
                new AddressResponse(
                        tutor.getUser().getDetailAddress(),
                        district == null ? null : district.getId(),
                        district == null ? null : district.getName(),
                        district == null || district.getProvince() == null ? null : district.getProvince().getId(),
                        district == null || district.getProvince() == null ? null : district.getProvince().getName()),
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
