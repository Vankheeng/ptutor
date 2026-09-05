package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StudyingRequestUpdateRequest(
        UUID subjectId,
        UUID gradeId,

        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @Size(max = 255, message = "Note must not exceed 255 characters")
        String note,

        UUID districtId,

        @Size(max = 255, message = "Detail address must not exceed 255 characters")
        String detailAddress,

        @DecimalMin(value = "0.0", inclusive = true, message = "Minimum price must not be negative")
        BigDecimal minPrice,

        @DecimalMin(value = "0.0", inclusive = true, message = "Maximum price must not be negative")
        BigDecimal maxPrice,

        @Size(max = 10000, message = "Learning goals must not exceed 10000 characters")
        String learningGoals,

        LearningMode learningMode,

        @Size(max = 500, message = "Preferred schedule must not exceed 500 characters")
        String preferredSchedule,

        List<@Valid Availability> availabilities) {

    @AssertTrue(message = "Minimum price must not exceed maximum price")
    public boolean hasValidPriceRange() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public boolean isEmpty() {
        return subjectId == null
                && gradeId == null
                && quantity == null
                && title == null
                && description == null
                && note == null
                && districtId == null
                && detailAddress == null
                && minPrice == null
                && maxPrice == null
                && learningGoals == null
                && learningMode == null
                && preferredSchedule == null
                && availabilities == null;
    }

    public record Availability(
            @Min(value = 1, message = "Day of week must be between 1 and 7")
            @Max(value = 7, message = "Day of week must be between 1 and 7")
            Integer dayOfWeek,

            LocalTime startTime,

            LocalTime endTime) {

        @AssertTrue(message = "End time must be after start time")
        public boolean isTimeRangeValid() {
            return startTime == null || endTime == null || endTime.isAfter(startTime);
        }
    }
}
