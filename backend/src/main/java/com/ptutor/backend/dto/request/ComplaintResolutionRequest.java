package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintResolutionRequest(
        @NotBlank(message = "Resolution is required")
        @Size(max = 5000, message = "Resolution must not exceed 5000 characters")
        String resolution) {
}
