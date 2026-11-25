package com.ptutor.backend.dto.response;

import java.math.BigDecimal;

public record AdminTransactionSummaryResponse(
        BigDecimal moneyIn,
        BigDecimal moneyOut,
        BigDecimal net,
        long internalCount) {
}
