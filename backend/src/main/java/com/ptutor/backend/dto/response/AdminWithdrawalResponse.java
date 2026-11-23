package com.ptutor.backend.dto.response;

import java.util.UUID;

public record AdminWithdrawalResponse(
        WithdrawalResponse withdrawal,
        UUID userId,
        String userName,
        String userEmail,
        String accountNumber) {
}
