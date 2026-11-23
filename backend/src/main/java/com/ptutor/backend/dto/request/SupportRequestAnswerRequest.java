package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportRequestAnswerRequest(
        @NotBlank(message = "Answer is required")
        @Size(max = 4000, message = "Answer must not exceed 4000 characters")
        String answer) {
}
