package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletBalanceResponse(
        UUID walletId,
        UUID userId,
        BigDecimal balance,
        BigDecimal pendingBalance,
        String currency,
        LocalDateTime updatedAt) {
}
