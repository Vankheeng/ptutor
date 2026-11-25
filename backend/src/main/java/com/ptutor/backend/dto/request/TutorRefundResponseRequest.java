package com.ptutor.backend.dto.request;

import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LessonOutcome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TutorRefundResponseRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,
        @NotNull(message = "Lesson positions are required")
        List<@Valid LessonPosition> lessonPositions,
        @Size(max = 5, message = "At most 5 evidences can be submitted")
        List<ComplaintCreateRequest.@Valid Evidence> evidences) {

    public record LessonPosition(
            @NotNull(message = "Lesson ID is required")
            UUID lessonId,
            @NotNull(message = "Position is required")
            LessonOutcome position) {
    }
}
