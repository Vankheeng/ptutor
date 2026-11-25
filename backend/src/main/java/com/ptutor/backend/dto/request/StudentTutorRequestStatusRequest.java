package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.ApplicationStatus;

import jakarta.validation.constraints.NotNull;

public record StudentTutorRequestStatusRequest(
        @NotNull(message = "Status is required") ApplicationStatus status) {
}
