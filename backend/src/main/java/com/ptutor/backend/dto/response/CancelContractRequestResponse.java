package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.CancelContractRequestStatus;

public record CancelContractRequestResponse(
        UUID id,
        UUID contractId,
        UUID requestedByUserId,
        CancelContractRequestStatus status,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
