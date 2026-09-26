package com.ptutor.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ComplaintReassignmentRequest(
        @NotNull(message = "Employee ID is required")
        UUID employeeId) {
}
