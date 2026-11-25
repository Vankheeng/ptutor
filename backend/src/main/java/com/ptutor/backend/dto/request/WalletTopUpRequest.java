package com.ptutor.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WalletTopUpRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "10000", message = "Amount must be at least 10000 VND")
        @DecimalMax(value = "100000000", message = "Amount must not exceed 100000000 VND")
        @Digits(integer = 9, fraction = 0, message = "Amount must be a whole VND amount")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Bank code must be alphanumeric")
        @Size(max = 20, message = "Bank code must not exceed 20 characters")
        String bankCode,

        @Pattern(regexp = "^(vn|en)?$", message = "Locale must be vn or en")
        String locale) {
}
