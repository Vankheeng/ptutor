package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteWithdrawalRequest(
        @NotBlank @Size(max = 100) String transferReference) {
}
