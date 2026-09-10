package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBankAccountRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String bankCode,
        @NotBlank @Size(max = 100) String bankName,
        @NotBlank @Pattern(regexp = "[0-9]{6,34}") String accountNumber,
        @NotBlank @Size(max = 150) String accountHolderName) {
}
