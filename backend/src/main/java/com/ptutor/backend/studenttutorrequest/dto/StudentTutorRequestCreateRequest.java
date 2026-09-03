package com.ptutor.backend.studenttutorrequest.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentTutorRequestCreateRequest(
        @NotNull(message = "Teaching request ID is required") UUID teachingRequestId,
        @NotNull(message = "Grade ID is required") UUID gradeId,
        @DecimalMin(value = "0.0", inclusive = true, message = "Proposed price must not be negative")
        BigDecimal proposedPrice,
        @NotNull(message = "Learning mode is required") LearningMode learningMode,
        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters") String preferredSchedule,
        @Size(max = 10000, message = "Message must not exceed 10000 characters") String message) {
}
