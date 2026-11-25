package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectWithdrawalRequest(@NotBlank @Size(max = 500) String reason) {
}
