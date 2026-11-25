package com.ptutor.backend.event;

import java.util.Map;
import java.util.UUID;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;

public record NotificationDomainEvent(
        UUID recipientUserId,
        NotificationEventType eventType,
        NotificationReferenceType referenceType,
        String referenceId,
        Map<String, String> data,
        String deduplicationKey) {

    public NotificationDomainEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static NotificationDomainEvent of(
            UUID recipientUserId,
            NotificationEventType eventType,
            NotificationReferenceType referenceType,
            UUID referenceId,
            Map<String, String> data) {
        return new NotificationDomainEvent(
                recipientUserId, eventType, referenceType,
                referenceId == null ? null : referenceId.toString(), data, null);
    }

    public static NotificationDomainEvent deduplicated(
            UUID recipientUserId,
            NotificationEventType eventType,
            NotificationReferenceType referenceType,
            UUID referenceId,
            Map<String, String> data,
            String deduplicationKey) {
        return new NotificationDomainEvent(
                recipientUserId, eventType, referenceType,
                referenceId == null ? null : referenceId.toString(), data, deduplicationKey);
    }
}
