package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TutorProfileRejectionRequest(
        @NotBlank @Size(min = 10, max = 500) String reason) {
}
