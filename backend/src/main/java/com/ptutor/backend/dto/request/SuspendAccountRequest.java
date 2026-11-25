package com.ptutor.backend.dto.request;

import java.time.Instant;

import com.ptutor.backend.entity.enums.SuspensionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SuspendAccountRequest(
        @NotNull SuspensionType type,
        @NotBlank @Size(min = 10, max = 1000) String reason,
        Instant suspendedUntil) {
}
