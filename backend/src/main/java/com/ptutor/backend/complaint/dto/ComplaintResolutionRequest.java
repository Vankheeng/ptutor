package com.ptutor.backend.complaint.dto;

import com.ptutor.backend.entity.enums.ComplaintStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintResolutionRequest(
        @NotNull(message = "Complaint status is required")
        ComplaintStatus status,

        @Size(max = 10000, message = "Resolution must not exceed 10000 characters")
        String resolution) {
}
