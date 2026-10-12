package com.ptutor.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Area;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Availability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A tutor's search for students. The tutor does not need to have a teaching posting. */
public record StudentRecommendationRequest(
        @Valid Filters filters,
        UUID useTeachingRequestId) {

    public record Filters(
            UUID subjectId,
            @Size(max = 20, message = "At most 20 grades are allowed") List<@NotNull UUID> gradeIds,
            ModePreference learningMode,
            @Valid Area area,
            @PositiveOrZero(message = "Expected price must not be negative") BigDecimal expectedPrice,
            @Size(max = 42, message = "At most 42 availability slots are allowed")
            List<@Valid @NotNull Availability> availabilities,
            @Size(max = 10, message = "At most 10 target segments are allowed") List<String> targetSegmentCodes) {
    }
}
