package com.ptutor.backend.dto.response;

import java.util.UUID;

public record NotificationCampaignStatisticsResponse(
        UUID notificationId, int recipientCount, long readCount, long unreadCount) {
}
