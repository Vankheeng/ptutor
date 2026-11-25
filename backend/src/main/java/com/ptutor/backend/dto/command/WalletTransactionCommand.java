package com.ptutor.backend.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;

public record WalletTransactionCommand(
        UUID userId,
        BigDecimal amount,
        WalletTransactionPurpose purpose,
        ReferenceType referenceType,
        UUID referenceId,
        String description,
        String idempotencyKey) {
}
