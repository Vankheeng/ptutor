package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateNotificationCampaignRequest(
        @NotBlank @Size(min = 5, max = 255) String title,
        @NotBlank @Size(min = 10, max = 5000) String content,
        @NotNull NotificationCategory category,
        @NotNull NotificationAudience audience) {
}
