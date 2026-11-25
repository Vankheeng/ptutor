package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.entity.enums.NotificationRecordType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignDistributionServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    private NotificationCampaignDistributionService service;
    private UUID campaignId;
    private Notification campaign;

    @BeforeEach
    void setUp() {
        service = new NotificationCampaignDistributionService(
                notificationRepository, userRepository,
                Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.UTC));
        campaignId = UUID.randomUUID();
        User creator = new User();
        creator.setId(UUID.randomUUID());
        campaign = Notification.builder()
                .recordType(NotificationRecordType.BROADCAST_MASTER)
                .title("Service update")
                .content("A new service version is now available.")
                .type(NotificationType.SYSTEM)
                .category(NotificationCategory.SERVICE_UPDATE)
                .audience(NotificationAudience.STUDENT)
                .campaignStatus(NotificationCampaignStatus.DRAFT)
                .createdByUser(creator)
                .updatedByUser(creator)
                .isRead(false)
                .build();
        campaign.setId(campaignId);
        campaign.setCreatedAt(LocalDateTime.of(2026, 9, 27, 7, 0));
        campaign.setUpdatedAt(LocalDateTime.of(2026, 9, 27, 7, 0));
        when(notificationRepository.findCampaignByIdForUpdate(campaignId)).thenReturn(Optional.of(campaign));
    }

    @Test
    void createsOneDeliveryPerEligibleRecipient() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userRepository.findActiveStudentUserIds()).thenReturn(List.of(first, second));
        when(notificationRepository.saveAndFlush(campaign)).thenReturn(campaign);

        var response = service.dispatchManual(campaignId, campaign.getCreatedByUser().getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, atLeastOnce()).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(delivery -> {
            assertThat(delivery.getRecordType()).isEqualTo(NotificationRecordType.BROADCAST_DELIVERY);
            assertThat(delivery.getParentNotification()).isSameAs(campaign);
            assertThat(delivery.getIsRead()).isFalse();
        });
        assertThat(response.status()).isEqualTo(NotificationCampaignStatus.SENT);
        assertThat(response.recipientCount()).isEqualTo(2);
    }

    @Test
    void rejectsAudienceWithNoEligibleRecipient() {
        when(userRepository.findActiveStudentUserIds()).thenReturn(List.of());

        assertThatThrownBy(() -> service.dispatchManual(campaignId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No active recipient");
    }
}
