package com.ptutor.backend.dto.request;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeachingRequestApprovalRequest(
        @Valid SubjectResolution subjectResolution) {

    public record SubjectResolution(
            @NotNull(message = "Subject resolution action is required")
            Action action,

            UUID subjectId,

            @Size(max = 100, message = "Subject name must not exceed 100 characters")
            String name,

            @Size(max = 500, message = "Subject description must not exceed 500 characters")
            String description) {
    }

    public enum Action {
        CREATE,
        USE_EXISTING
    }
}
