package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

/** Response for tutor offers viewed by the owner of a studying request. */
public record StudentTutorOfferResponse(
        UUID id,
        UUID tutorId,
        String tutorFirstName,
        String tutorLastName,
        String tutorEmail,
        String tutorPhone,
        UUID studyingRequestId,
        String studyingRequestTitle,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        RequestStatus studyingRequestStatus,
        LocalDateTime studyingRequestCreatedAt,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        BigDecimal proposedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        UUID preferredProvinceId,
        String preferredProvinceName,
        UUID preferredDistrictId,
        String preferredDistrictName,
        String message,
        ApplicationStatus status,
        String nextStep,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime acceptedAt) {
}
