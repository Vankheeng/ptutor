package com.ptutor.backend.dto.response;

import java.math.BigDecimal;

public record AdminWalletSummaryResponse(
        long walletCount,
        BigDecimal availableBalance,
        BigDecimal pendingWithdrawalBalance,
        long heldContractCount,
        BigDecimal heldTuition) {
}
