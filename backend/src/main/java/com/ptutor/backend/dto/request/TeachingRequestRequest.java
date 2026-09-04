package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.TeachingMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TeachingRequestRequest(
        UUID subjectId,

        @Size(max = 100, message = "Custom subject name must not exceed 100 characters")
        String customSubjectName,

        @NotEmpty(message = "At least one grade is required")
        List<@NotNull(message = "Grade ID must not be null") UUID> gradeIds,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 255, message = "Note must not exceed 255 characters")
        String note,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        List<@NotNull(message = "District ID must not be null") UUID> districtIds,

        @Size(max = 255, message = "Detail address must not exceed 255 characters")
        String detailAddress,

        @NotNull(message = "Expected price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Expected price must not be negative")
        BigDecimal expectedPrice,

        @NotNull(message = "Teaching mode is required")
        TeachingMode teachingMode,

        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        String preferredSchedule,

        @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description,

        List<@Valid Availability> availabilities) {

    @AssertTrue(message = "Exactly one of subjectId or customSubjectName must be provided")
    public boolean hasValidSubjectSource() {
        boolean existingSubject = subjectId != null;
        boolean customSubject = customSubjectName != null && !customSubjectName.isBlank();
        return existingSubject ^ customSubject;
    }

    public record Availability(
            @NotNull(message = "Day of week is required")
            @Min(value = 1, message = "Day of week must be between 1 and 7")
            @Max(value = 7, message = "Day of week must be between 1 and 7")
            Integer dayOfWeek,

            @NotNull(message = "Start time is required")
            LocalTime startTime,

            @NotNull(message = "End time is required")
            LocalTime endTime) {

        @AssertTrue(message = "End time must be after start time")
        public boolean isTimeRangeValid() {
            return startTime == null || endTime == null || endTime.isAfter(startTime);
        }
    }
}
