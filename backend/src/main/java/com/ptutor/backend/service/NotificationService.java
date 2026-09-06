package com.ptutor.backend.service;

import org.springframework.stereotype.Service;

import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void createCertificateReviewNotification(
            User recipient, String certificateId, String certificateName, boolean approved, String rejectionReason) {
        String title = approved ? "Certificate approved" : "Certificate rejected";
        String content = approved
                ? "Your certificate \"" + certificateName + "\" has been approved."
                : "Your certificate \"" + certificateName + "\" was rejected. Reason: " + rejectionReason;
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .title(title)
                .content(content)
                .referenceId(certificateId)
                .type(NotificationType.SYSTEM)
                .isRead(false)
                .build());
    }
}
