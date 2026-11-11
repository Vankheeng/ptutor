package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import com.ptutor.backend.entity.enums.PaymentPeriod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tutor-side contract terms. Grade is optional: when omitted, the grade from
 * the accepted student application is used. When supplied it must belong to
 * the teaching request.
 */
public record TutorContractCreateRequest(
        UUID gradeId,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        BigDecimal price,

        @NotNull(message = "Payment period is required")
        PaymentPeriod paymentPeriod,

        Integer totalLessons,

        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        String preferredSchedule,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        List<ContractScheduleRequest> schedules) {

    public TutorContractCreateRequest(UUID gradeId, BigDecimal price, PaymentPeriod paymentPeriod,
            Integer totalLessons, String preferredSchedule, LocalDate startDate, LocalDate endDate) {
        this(gradeId, price, paymentPeriod, totalLessons, preferredSchedule, startDate, endDate, null);
    }
}
