package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;

public record PaymentInitiationResponse(
        UUID paymentId,
        PaymentType paymentType,
        BigDecimal amount,
        PaymentStatus status,
        String paymentUrl,
        LocalDateTime expiresAt) {
}
