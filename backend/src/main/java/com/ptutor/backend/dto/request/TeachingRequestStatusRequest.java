package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.RequestStatus;

import jakarta.validation.constraints.NotNull;

public record TeachingRequestStatusRequest(
        @NotNull(message = "Status is required") RequestStatus status) {
}
