package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.LearningMode;

public record StudentTutorRequestResponse(
        UUID id,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String studentEmail,
        UUID gradeId,
        String gradeName,
        UUID teachingRequestId,
        UUID contractId,
        BigDecimal proposedPrice,
        LearningMode learningMode,
        String preferredSchedule,
        UUID preferredProvinceId,
        String preferredProvinceName,
        UUID preferredDistrictId,
        String preferredDistrictName,
        String detailAddress,
        String message,
        ApplicationStatus status,
        String nextStep,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public StudentTutorRequestResponse withContractId(UUID value) {
        return new StudentTutorRequestResponse(
                id, studentId, studentFirstName, studentLastName, studentEmail, gradeId, gradeName,
                teachingRequestId, value, proposedPrice, learningMode, preferredSchedule,
                preferredProvinceId, preferredProvinceName, preferredDistrictId, preferredDistrictName,
                detailAddress, message, status, nextStep, createdAt, updatedAt);
    }

}
