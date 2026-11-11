package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.ComplaintFaultParty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintAcceptRequest(
        @NotBlank(message = "Resolution is required")
        @Size(max = 5000, message = "Resolution must not exceed 5000 characters")
        String resolution,
        @NotNull(message = "Fault party is required")
        ComplaintFaultParty faultParty) {
}
