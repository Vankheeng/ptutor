package com.ptutor.backend.dto.response;

import java.util.UUID;
import com.ptutor.backend.dto.enums.AdminTransactionFlow;

public record AdminTransactionResponse(
        UUID userId,
        String userName,
        String userEmail,
        String purpose,
        AdminTransactionFlow flow,
        FinancialTransactionResponse transaction) {
}
