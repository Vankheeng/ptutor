package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.entity.enums.WalletTransactionStatus;
import com.ptutor.backend.entity.enums.WalletTransactionType;

public record WalletTransactionResponse(
        UUID transactionId,
        UUID walletId,
        WalletTransactionType transactionType,
        WalletTransactionPurpose purpose,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal pendingBalanceAfter,
        ReferenceType referenceType,
        UUID referenceId,
        String description,
        WalletTransactionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
