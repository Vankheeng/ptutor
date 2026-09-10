package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;

public record WithdrawalResponse(
        UUID withdrawalId,
        UUID walletId,
        BigDecimal amount,
        String bankCode,
        String bankName,
        String maskedAccountNumber,
        String accountHolderName,
        String note,
        WithdrawalRequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
