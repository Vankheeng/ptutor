package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;

    @Test
    void createCertificateReviewNotificationTargetsTutorAndReferencesCertificate() {
        NotificationService service = new NotificationService(notificationRepository);
        User tutorUser = User.builder().build();

        service.createCertificateReviewNotification(
                tutorUser, "certificate-id", "IELTS 8.0", false, "Image is unreadable");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getUser()).isSameAs(tutorUser);
        assertThat(notification.getReferenceId()).isEqualTo("certificate-id");
        assertThat(notification.getType()).isEqualTo(NotificationType.SYSTEM);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getContent()).contains("Image is unreadable");
    }
}
