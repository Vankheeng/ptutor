package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LearningMode;

public record StudentTutorRequestResponse(
        UUID id,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String studentEmail,
        String studentPhone,
        UUID gradeId,
        String gradeName,
        UUID teachingRequestId,
        UUID contractId,
        UUID previousContractId,
        ContractStatus previousContractStatus,
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
                id, studentId, studentFirstName, studentLastName, studentEmail, studentPhone, gradeId, gradeName,
                teachingRequestId, value, previousContractId, previousContractStatus, proposedPrice, learningMode, preferredSchedule,
                preferredProvinceId, preferredProvinceName, preferredDistrictId, preferredDistrictName,
                detailAddress, message, status, nextStep, createdAt, updatedAt);
    }

    public StudentTutorRequestResponse withPreviousContractId(UUID value) {
        return new StudentTutorRequestResponse(
                id, studentId, studentFirstName, studentLastName, studentEmail, studentPhone, gradeId, gradeName,
                teachingRequestId, contractId, value, previousContractStatus, proposedPrice, learningMode, preferredSchedule,
                preferredProvinceId, preferredProvinceName, preferredDistrictId, preferredDistrictName,
                detailAddress, message, status, nextStep, createdAt, updatedAt);
    }

    public StudentTutorRequestResponse withPreviousContractStatus(ContractStatus value) {
        return new StudentTutorRequestResponse(
                id, studentId, studentFirstName, studentLastName, studentEmail, studentPhone, gradeId, gradeName,
                teachingRequestId, contractId, previousContractId, value, proposedPrice, learningMode, preferredSchedule,
                preferredProvinceId, preferredProvinceName, preferredDistrictId, preferredDistrictName,
                detailAddress, message, status, nextStep, createdAt, updatedAt);
    }

}
