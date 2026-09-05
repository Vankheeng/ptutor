package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.RequestStatus;

import jakarta.validation.constraints.NotNull;

public record StudyingRequestStatusRequest(
        @NotNull(message = "Status is required") RequestStatus status) {
}
