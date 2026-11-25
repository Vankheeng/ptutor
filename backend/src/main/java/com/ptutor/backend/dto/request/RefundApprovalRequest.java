package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LessonOutcome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundApprovalRequest(
        @NotNull(message = "Expected amount is required")
        @DecimalMin(value = "0", message = "Expected amount must not be negative")
        BigDecimal expectedAmount,
        @NotNull(message = "Lesson decisions are required")
        List<@Valid LessonDecision> lessonDecisions,
        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note) {

    public record LessonDecision(
            @NotNull(message = "Lesson ID is required")
            UUID lessonId,
            @NotNull(message = "Decision is required")
            LessonOutcome decision) {
    }
}
