package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.NotificationReadStatus;
import com.ptutor.backend.dto.response.NotificationReadAllResponse;
import com.ptutor.backend.dto.response.NotificationResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.UnreadCountResponse;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateFactory templateFactory;

    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, new NotificationTemplateFactory());
    }

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
            NotificationTemplateFactory templateFactory) {
        this.notificationRepository = notificationRepository;
        this.templateFactory = templateFactory;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createFromEvent(NotificationDomainEvent event) {
        NotificationContent template = templateFactory.create(event);
        Notification notification = Notification.builder()
                .user(userReference(event.recipientUserId()))
                .title(template.title())
                .content(template.content())
                .referenceId(event.referenceId())
                .type(template.type())
                .eventType(event.eventType())
                .referenceType(event.referenceType())
                .deduplicationKey(event.deduplicationKey())
                .isRead(false)
                .build();
        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException duplicateDeduplicationKey) {
            if (event.deduplicationKey() == null) {
                throw duplicateDeduplicationKey;
            }
            // A duplicate reminder is an expected idempotency result.
        }
    }

    /** Kept for source compatibility with the original certificate service. */
    @Transactional
    public void createCertificateReviewNotification(
            User recipient, String certificateId, String certificateName, boolean approved, String rejectionReason) {
        NotificationEventType eventType = approved
                ? NotificationEventType.CERTIFICATE_APPROVED
                : NotificationEventType.CERTIFICATE_REJECTED;
        NotificationContent template = templateFactory.create(new NotificationDomainEvent(
                recipient.getId(), eventType, NotificationReferenceType.CERTIFICATE, certificateId,
                java.util.Map.of("name", certificateName == null ? "" : certificateName,
                        "reason", rejectionReason == null ? "" : rejectionReason), null));
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .title(template.title())
                .content(template.content())
                .referenceId(certificateId)
                .type(template.type())
                .eventType(eventType)
                .referenceType(NotificationReferenceType.CERTIFICATE)
                .isRead(false)
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findMine(
            UUID userId, NotificationReadStatus status, Pageable pageable) {
        Page<Notification> notifications = status == null
                ? notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findAllByUser_IdAndIsReadOrderByCreatedAtDesc(
                        userId, status == NotificationReadStatus.READ, pageable);
        return PageResponse.from(notifications, notifications.getContent().stream()
                .map(this::toResponse)
                .toList());
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "Notification not found: " + notificationId));
        notification.setIsRead(true);
        return toResponse(notificationRepository.saveAndFlush(notification));
    }

    @Transactional
    public NotificationReadAllResponse markAllRead(UUID userId) {
        return new NotificationReadAllResponse(notificationRepository.markAllAsRead(userId));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByUser_IdAndIsRead(userId, false));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getTitle(), notification.getContent(), notification.getType(),
                notification.getEventType(), notification.getReferenceType(), notification.getReferenceId(),
                Boolean.TRUE.equals(notification.getIsRead()), notification.getCreatedAt());
    }

    private User userReference(UUID userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    @Transactional
    public void createTeachingRequestReviewNotification(
            User recipient,
            String teachingRequestId,
            String requestTitle,
            boolean approved,
            String rejectionReason,
            BigDecimal refundedAmount) {
        NotificationEventType eventType = approved
                ? NotificationEventType.TEACHING_REQUEST_APPROVED
                : NotificationEventType.TEACHING_REQUEST_REJECTED;
        NotificationContent template = templateFactory.create(new NotificationDomainEvent(
                recipient.getId(), eventType, NotificationReferenceType.TEACHING_REQUEST, teachingRequestId,
                java.util.Map.of(
                        "name", requestTitle == null ? "" : requestTitle,
                        "reason", rejectionReason == null ? "" : rejectionReason,
                        "amount", refundedAmount == null ? "" : refundedAmount.toPlainString()),
                null));
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .title(template.title())
                .content(template.content())
                .referenceId(teachingRequestId)
                .type(template.type())
                .eventType(eventType)
                .referenceType(NotificationReferenceType.TEACHING_REQUEST)
                .isRead(false)
                .build());
    }
}
