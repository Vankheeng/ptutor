package com.ptutor.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StudyingRequestApprovalRequest(
        @NotNull(message = "Subject resolution is required")
        @Valid TeachingRequestApprovalRequest.SubjectResolution subjectResolution) {
}
