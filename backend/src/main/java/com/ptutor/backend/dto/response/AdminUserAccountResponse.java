package com.ptutor.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.UserStatus;

public record AdminUserAccountResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserRole role,
        UserStatus status,
        SuspensionType suspensionType,
        String suspensionReason,
        Instant suspendedAt,
        Instant suspendedUntil,
        UUID suspendedByUserId,
        Instant reactivatedAt,
        UUID reactivatedByUserId,
        String reactivationReason,
        int suspensionCount,
        Instant createdAt,
        Instant updatedAt) {
}
