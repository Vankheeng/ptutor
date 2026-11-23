package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminWalletResponse(
        UUID walletId,
        UUID userId,
        String userName,
        String userEmail,
        String userType,
        BigDecimal balance,
        BigDecimal pendingBalance) {
}
