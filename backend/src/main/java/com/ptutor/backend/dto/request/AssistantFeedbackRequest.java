package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Đánh giá 👍 / 👎 một câu trả lời (docs/support-chatbot/07-api.md mục 7.2). Gửi lại → ghi đè. */
public record AssistantFeedbackRequest(
        @NotNull(message = "rating is required")
        Integer rating,
        @Pattern(regexp = "^(WRONG|NOT_HELPFUL|OUTDATED|OTHER)$", message = "reason is not supported")
        String reason,
        @Size(max = 500, message = "comment must not exceed 500 characters")
        String comment) {

    @AssertTrue(message = "rating must be 1 or -1")
    public boolean isRatingValid() {
        return rating == null || rating == 1 || rating == -1;
    }
}
