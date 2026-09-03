package com.ptutor.backend.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintEvidenceRequest(
        @NotBlank(message = "Evidence URL is required")
        @Size(max = 500, message = "Evidence URL must not exceed 500 characters")
        String fileUrl,

        @NotBlank(message = "Evidence file type is required")
        @Size(max = 100, message = "Evidence file type must not exceed 100 characters")
        String fileType) {
}
