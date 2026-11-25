package com.ptutor.backend.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ComplaintEvidenceSubmissionRequest(
        @NotEmpty(message = "At least one evidence is required")
        @Size(max = 5, message = "At most 5 evidences can be submitted at once")
        List<ComplaintCreateRequest.@Valid Evidence> evidences) {
}
