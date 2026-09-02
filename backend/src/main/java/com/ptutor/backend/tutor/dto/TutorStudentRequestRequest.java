package com.ptutor.backend.tutor.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.ptutor.backend.entity.enums.TeachingMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TutorStudentRequestRequest(
        @NotNull(message = "Studying request ID is required") UUID studyingRequestId,
        @NotNull(message = "Grade ID is required") UUID gradeId,
        @NotNull(message = "Proposed price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Proposed price must not be negative")
        BigDecimal proposedPrice,
        @NotNull(message = "Teaching mode is required") TeachingMode teachingMode,
        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        @Pattern(regexp = ".*\\S.*", message = "Preferred schedule must not be blank")
        String preferredSchedule,
        @Pattern(regexp = ".*\\S.*", message = "Message must not be blank")
        String message) {
}
