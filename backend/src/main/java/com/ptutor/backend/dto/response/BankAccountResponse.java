package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record BankAccountResponse(
        UUID bankAccountId,
        UUID userId,
        String bankCode,
        String bankName,
        String maskedAccountNumber,
        String accountHolderName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
