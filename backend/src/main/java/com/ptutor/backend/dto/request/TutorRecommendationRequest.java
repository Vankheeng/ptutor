package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A student's search for tutors: the filters are the main input, the free text is optional. */
public record TutorRecommendationRequest(
        @Valid Filters filters,
        @Size(max = 500, message = "Semantic text must not exceed 500 characters") String semanticText,
        @Size(max = 500, message = "Query text must not exceed 500 characters") String queryText,
        UUID useStudyingRequestId) {

    public record Filters(
            UUID subjectId,
            UUID gradeId,
            ModePreference learningMode,
            @Valid Area area,
            @Valid Price price,
            @Size(max = 42, message = "At most 42 availability slots are allowed")
            List<@Valid @NotNull Availability> availabilities,
            @Size(max = 20, message = "At most 20 style tags are allowed") List<String> styleTagCodes,
            @Size(max = 20, message = "At most 20 goal tags are allowed") List<String> goalTagCodes,
            @Size(max = 20, message = "At most 20 topic tags are allowed") List<String> topicTagCodes) {
    }

    public record Area(
            AreaScope scope,
            @Size(max = 50, message = "At most 50 districts are allowed") List<UUID> districtIds,
            UUID provinceId) {

        @AssertTrue(message = "District scope requires at least one district")
        public boolean isDistrictScopeValid() {
            return scope != AreaScope.DISTRICTS || (districtIds != null && !districtIds.isEmpty());
        }
    }

    public record Price(
            @PositiveOrZero(message = "Minimum price must not be negative") BigDecimal min,
            @PositiveOrZero(message = "Maximum price must not be negative") BigDecimal max) {

        @AssertTrue(message = "Minimum price must not exceed maximum price")
        public boolean isRangeValid() {
            return min == null || max == null || min.compareTo(max) <= 0;
        }
    }

    /** {@code dayOfWeek} follows ISO-8601: 1 = Monday ... 7 = Sunday. */
    public record Availability(
            @NotNull(message = "Day of week is required")
            @Min(value = 1, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
            @Max(value = 7, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
            Integer dayOfWeek,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime) {

        @AssertTrue(message = "End time must be after start time")
        public boolean isTimeRangeValid() {
            return startTime == null || endTime == null || endTime.isAfter(startTime);
        }
    }
}
