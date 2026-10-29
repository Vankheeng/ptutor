package com.ptutor.backend.dto.request;

import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LessonOutcome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotNull(message = "Contract ID is required")
        UUID contractId,
        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,
        @NotEmpty(message = "Choose at least one lesson")
        @Size(max = 50, message = "At most 50 lessons can be disputed")
        List<@Valid DisputedLesson> lessons,
        @Size(max = 5, message = "At most 5 evidences can be submitted")
        List<ComplaintCreateRequest.@Valid Evidence> evidences) {

    /**
     * @param claimedOutcome TUTOR_ABSENT or STUDENT_ABSENT
     */
    public record DisputedLesson(
            @NotNull(message = "Lesson ID is required")
            UUID lessonId,
            @NotNull(message = "Claimed outcome is required")
            LessonOutcome claimedOutcome) {
    }
}
