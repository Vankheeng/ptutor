package com.ptutor.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.entity.enums.UserStatus;

public record AdminEmployeeDetailResponse(
        UUID employeeId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        String maskedCitizenId,
        AddressResponse address,
        EmployeeRole systemRole,
        EmployeeJobFunction jobFunction,
        UserStatus status,
        String accessRevocationReason,
        Instant accessRevokedAt,
        UUID accessRevokedByUserId,
        String reactivationReason,
        Instant reactivatedAt,
        UUID reactivatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
