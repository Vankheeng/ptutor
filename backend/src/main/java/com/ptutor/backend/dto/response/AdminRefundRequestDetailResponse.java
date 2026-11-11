package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ComplaintFaultParty;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.PaymentMethod;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

/**
 * Everything a reviewer needs on one screen. The request fields mirror {@link RefundRequestResponse}.
 */
public record AdminRefundRequestDetailResponse(
        UUID id,
        UUID contractId,
        RefundRequestStatus status,
        String description,
        List<RefundRequestResponse.DisputedLesson> disputedLessons,
        List<RefundRequestResponse.Evidence> evidences,
        String tutorResponse,
        LocalDateTime tutorRespondedAt,
        LocalDateTime tutorResponseDeadline,
        BigDecimal refundAmount,
        BigDecimal tutorShareAmount,
        String rejectionReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Party student,
        Party tutor,
        Reviewer reviewer,
        ContractSummary contract,
        List<PaymentSummary> payments,
        List<ComplaintSummary> complaints,
        Calculation calculation,
        String note) {

    public record Party(UUID userId, String name, String email) {
    }

    public record Reviewer(UUID employeeId, UUID userId, String name) {
    }

    public record ContractSummary(
            UUID id,
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

    public record PaymentSummary(
            UUID id,
            UUID paymentInstallmentId,
            PaymentMethod paymentMethod,
            PaymentStatus status,
            BigDecimal amount,
            BigDecimal refundedAmount,
            LocalDateTime paidAt) {
    }

    public record ComplaintSummary(
            UUID complaintId, String title, ComplaintStatus status, ComplaintFaultParty faultParty) {
    }

    /**
     * Before approval the amounts preview the reviewer's decisions where given and the student's claims otherwise;
     * after approval they are the snapshot.
     */
    public record Calculation(
            BigDecimal unitPrice,
            int tutorAbsentRate,
            int studentAbsentRate,
            BigDecimal paidAmount,
            BigDecimal previouslyRefundedAmount,
            BigDecimal refundableAmount,
            BigDecimal refundAmount,
            BigDecimal tutorShareAmount,
            int undecidedLessons,
            List<String> blockers) {
    }
}
