package com.ptutor.backend.service;

import com.ptutor.backend.entity.enums.NotificationType;

public record NotificationContent(NotificationType type, String title, String content) {
}
