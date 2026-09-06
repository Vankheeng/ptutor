package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Partial update for a pending contract. The service enforces which fields
 * each contract creator may modify.
 */
public record ContractUpdateRequest(
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        BigDecimal price,

        @Size(max = 100, message = "Payment period must not exceed 100 characters")
        String paymentPeriod,

        @Positive(message = "Total lessons must be greater than zero")
        Integer totalLessons,

        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        String preferredSchedule,

        LocalDate startDate,

        LocalDate endDate,

        UUID gradeId) {

    public boolean isEmpty() {
        return price == null
                && paymentPeriod == null
                && totalLessons == null
                && preferredSchedule == null
                && startDate == null
                && endDate == null
                && gradeId == null;
    }
}
