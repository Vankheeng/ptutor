package com.ptutor.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.UserStatus;

public record AdminEmployeeSummaryResponse(
        UUID employeeId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phone,
        EmployeeJobFunction jobFunction,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
