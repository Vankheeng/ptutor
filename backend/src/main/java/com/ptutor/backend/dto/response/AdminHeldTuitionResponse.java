package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminHeldTuitionResponse(
        UUID contractId,
        UUID studentUserId,
        String studentName,
        String studentEmail,
        UUID tutorUserId,
        String tutorName,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal tutorPaidAmount,
        BigDecimal heldAmount,
        LocalDateTime lastPaidAt) {
}
