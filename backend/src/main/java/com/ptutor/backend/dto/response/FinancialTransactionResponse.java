package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;

public record FinancialTransactionResponse(
        UUID id,
        FinancialTransactionSource source,
        String type,
        String method,
        String status,
        BigDecimal amount,
        UUID referenceId,
        String description,
        LocalDateTime occurredAt) {
}
