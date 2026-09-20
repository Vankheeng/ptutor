package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;

public record ContractPaymentInstallmentResponse(
        UUID id,
        Integer sequenceNumber,
        PaymentPeriod paymentPeriod,
        BigDecimal amount,
        LocalDate dueDate,
        UUID lessonId,
        PaymentInstallmentStatus status) {
}
