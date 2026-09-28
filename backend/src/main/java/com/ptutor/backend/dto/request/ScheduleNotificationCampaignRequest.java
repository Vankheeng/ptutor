package com.ptutor.backend.dto.request;

import java.time.Instant;
import jakarta.validation.constraints.NotNull;

public record ScheduleNotificationCampaignRequest(@NotNull Instant scheduledAt) {
}
