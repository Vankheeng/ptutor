package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Terms supplied when a student creates a contract or either participant
 * proposes a renewal. The remaining contract data is derived from the
 * accepted application or the contract being renewed.
 */
public record ContractTermsRequest(
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        BigDecimal price,

        @NotBlank(message = "Payment period is required")
        @Size(max = 100, message = "Payment period must not exceed 100 characters")
        String paymentPeriod,

        @NotNull(message = "Total lessons is required")
        @Positive(message = "Total lessons must be greater than zero")
        Integer totalLessons,

        @NotBlank(message = "Preferred schedule is required")
        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        String preferredSchedule,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate) {
}
