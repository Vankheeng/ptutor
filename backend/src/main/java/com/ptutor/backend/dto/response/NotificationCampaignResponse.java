package com.ptutor.backend.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;

public record NotificationCampaignResponse(
        UUID id, String title, String content, NotificationCategory category,
        NotificationAudience audience, NotificationCampaignStatus status,
        Instant scheduledAt, Instant processingStartedAt, Instant sentAt,
        int recipientCount, long readCount, long unreadCount, String failureReason,
        UUID createdByUserId, UUID updatedByUserId, Instant createdAt, Instant updatedAt) {
}
