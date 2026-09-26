package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactivateAccountRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
