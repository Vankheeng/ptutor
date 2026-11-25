package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintEvidenceRequest(
        @NotBlank(message = "Evidence request message is required")
        @Size(max = 5000, message = "Evidence request message must not exceed 5000 characters")
        String message) {
}
