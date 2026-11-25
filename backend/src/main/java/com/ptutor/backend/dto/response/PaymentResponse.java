package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.PaymentMethod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;

public record PaymentResponse(
        UUID id,
        PaymentType paymentType,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        BigDecimal amount,
        ReferenceType referenceType,
        UUID referenceId,
        UUID paymentInstallmentId,
        String transactionCode,
        String providerTransactionNo,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {
}
