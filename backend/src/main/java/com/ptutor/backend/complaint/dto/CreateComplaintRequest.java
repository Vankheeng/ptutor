package com.ptutor.backend.complaint.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateComplaintRequest(
        @NotNull(message = "Contract ID is required")
        UUID contractId,

        @NotBlank(message = "Complaint title is required")
        @Size(max = 255, message = "Complaint title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Complaint content is required")
        @Size(max = 10000, message = "Complaint content must not exceed 10000 characters")
        String content,

        @Size(max = 10, message = "A complaint may contain at most 10 evidences")
        List<@Valid ComplaintEvidenceRequest> evidences) {
}
