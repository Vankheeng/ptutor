package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminContractTransactionResponse(
        UUID id,
        String type,
        String userName,
        BigDecimal amount,
        String status,
        LocalDateTime occurredAt,
        String transactionCode) {
}
