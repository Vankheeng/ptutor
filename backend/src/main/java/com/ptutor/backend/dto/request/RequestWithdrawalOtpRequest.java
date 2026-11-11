package com.ptutor.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestWithdrawalOtpRequest(
        @NotNull @DecimalMin(value = "50000", message = "Withdrawal amount must be at least 50,000 VND")
        @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 500) String note) {}
