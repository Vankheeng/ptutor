package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentMethod;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.TeachingMode;

public record AdminComplaintDetailResponse(
        UUID id,
        String title,
        String content,
        ComplaintStatus status,
        String resolution,
        LocalDateTime resolvedAt,
        Complainant complainant,
        Reviewer reviewer,
        ContractDetail contract,
        List<EvidenceDetail> evidences,
        List<LessonDetail> lessons,
        List<InstallmentDetail> installments,
        List<PaymentDetail> payments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Complainant(UUID userId, String name, String email, UserRole role) {
    }

    public record Reviewer(UUID employeeId, UUID userId, String name) {
    }

    public record ContractDetail(
            UUID id,
            UUID studentUserId,
            String studentName,
            UUID tutorUserId,
            String tutorName,
            String subjectName,
            String gradeName,
            TeachingMode teachingMode,
            BigDecimal price,
            PaymentPeriod paymentPeriod,
            Integer totalLessons,
            LocalDate startDate,
            LocalDate endDate,
            ContractStatus status) {
    }

    public record EvidenceDetail(UUID id, String fileUrl, String fileType, LocalDateTime createdAt) {
    }

    public record LessonDetail(
            UUID id,
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TeachingMode teachingMode,
            LessonStatus status,
            String note) {
    }

    public record InstallmentDetail(
            UUID id,
            UUID lessonId,
            Integer sequenceNumber,
            PaymentPeriod paymentPeriod,
            BigDecimal amount,
            LocalDate dueDate,
            PaymentInstallmentStatus status) {
    }

    public record PaymentDetail(
            UUID id,
            UUID paymentInstallmentId,
            PaymentType paymentType,
            PaymentMethod paymentMethod,
            PaymentStatus status,
            BigDecimal amount,
            String transactionCode,
            String providerTransactionNo,
            LocalDateTime paidAt,
            LocalDateTime createdAt) {
    }
}
