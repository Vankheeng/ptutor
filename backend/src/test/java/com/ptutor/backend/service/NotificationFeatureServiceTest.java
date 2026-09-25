package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.ptutor.backend.dto.enums.NotificationReadStatus;
import com.ptutor.backend.dto.response.NotificationResponse;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationFeatureServiceTest {

    @Mock NotificationRepository notificationRepository;

    private NotificationService service;
    private UUID userId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, new NotificationTemplateFactory());
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
    }

    @Test
    void createsEventNotificationWithTypeAndReferenceMetadata() {
        NotificationDomainEvent event = NotificationDomainEvent.of(
                userId,
                NotificationEventType.PAYMENT_SUCCEEDED,
                NotificationReferenceType.PAYMENT,
                notificationId,
                Map.of("paymentType", "STUDYING_REQUEST_FEE"));

        service.createFromEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT);
        assertThat(saved.getEventType()).isEqualTo(NotificationEventType.PAYMENT_SUCCEEDED);
        assertThat(saved.getReferenceType()).isEqualTo(NotificationReferenceType.PAYMENT);
        assertThat(saved.getReferenceId()).isEqualTo(notificationId.toString());
        assertThat(saved.getIsRead()).isFalse();
    }

    @Test
    void findsOnlyRequestedReadStatusWithNewestFirstPage() {
        Notification notification = notification();
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(notificationRepository.findAllByUser_IdAndIsReadOrderByCreatedAtDesc(
                userId, false, pageable)).thenReturn(new PageImpl<>(java.util.List.of(notification), pageable, 1));

        var result = service.findMine(userId, NotificationReadStatus.UNREAD, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(notificationId);
        verify(notificationRepository).findAllByUser_IdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
    }

    @Test
    void marksOnlyNotificationOwnedByCurrentUserAsRead() {
        Notification notification = notification();
        when(notificationRepository.findByIdAndUser_Id(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.saveAndFlush(notification)).thenReturn(notification);

        NotificationResponse response = service.markRead(userId, notificationId);

        assertThat(response.isRead()).isTrue();
        verify(notificationRepository).findByIdAndUser_Id(notificationId, userId);
    }

    @Test
    void marksAllAndCountsUnreadNotificationsForCurrentUser() {
        when(notificationRepository.markAllAsRead(userId)).thenReturn(3);
        when(notificationRepository.countByUser_IdAndIsRead(userId, false)).thenReturn(2L);

        assertThat(service.markAllRead(userId).markedReadCount()).isEqualTo(3);
        assertThat(service.unreadCount(userId).unreadCount()).isEqualTo(2);
    }

    private Notification notification() {
        User user = new User();
        user.setId(userId);
        Notification value = Notification.builder()
                .user(user)
                .title("Payment successful")
                .content("Completed")
                .referenceId(notificationId.toString())
                .type(NotificationType.PAYMENT)
                .eventType(NotificationEventType.PAYMENT_SUCCEEDED)
                .referenceType(NotificationReferenceType.PAYMENT)
                .isRead(false)
                .build();
        value.setId(notificationId);
        value.setCreatedAt(LocalDateTime.now());
        return value;
    }
}
