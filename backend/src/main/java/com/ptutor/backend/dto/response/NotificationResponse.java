package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.NotificationType;

public record NotificationResponse(
        UUID id,
        String title,
        String content,
        NotificationType type,
        NotificationEventType eventType,
        NotificationReferenceType referenceType,
        String referenceId,
        boolean isRead,
        LocalDateTime createdAt) {
}
