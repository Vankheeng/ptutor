package com.ptutor.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWithdrawalRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 integer and 2 fractional digits")
        BigDecimal amount,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note) {
}
