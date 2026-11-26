package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.Size;

/** Only what is sent is changed. */
public record SavedSearchUpdateRequest(
        @Size(max = 120, message = "Name must not exceed 120 characters") String name,
        Boolean notifyEnabled) {
}
