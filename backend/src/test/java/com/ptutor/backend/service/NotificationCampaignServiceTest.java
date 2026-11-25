package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.request.CreateNotificationCampaignRequest;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.entity.enums.NotificationRecordType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationCampaignDistributionService distributionService;
    @Mock NotificationCampaignFailureService failureService;

    private NotificationCampaignService service;
    private UUID actorId;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new NotificationCampaignService(
                notificationRepository, userRepository, distributionService, failureService,
                Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.UTC));
        actorId = UUID.randomUUID();
        actor = User.builder().status(UserStatus.ACTIVE).build();
        actor.setId(actorId);
    }

    @Test
    void createsBroadcastMasterDraft() {
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });

        service.create(actorId, new CreateNotificationCampaignRequest(
                "System maintenance", "The system will be unavailable for maintenance.",
                NotificationCategory.MAINTENANCE, NotificationAudience.ALL));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification campaign = captor.getValue();
        assertThat(campaign.getRecordType()).isEqualTo(NotificationRecordType.BROADCAST_MASTER);
        assertThat(campaign.getCampaignStatus()).isEqualTo(NotificationCampaignStatus.DRAFT);
        assertThat(campaign.getUser()).isNull();
        assertThat(campaign.getCreatedByUser()).isSameAs(actor);
    }

    @Test
    void rejectsScheduleLessThanFiveMinutesAhead() {
        UUID campaignId = UUID.randomUUID();
        Notification campaign = Notification.builder()
                .recordType(NotificationRecordType.BROADCAST_MASTER)
                .campaignStatus(NotificationCampaignStatus.DRAFT)
                .build();
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(notificationRepository.findCampaignByIdForUpdate(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.schedule(
                actorId, campaignId, Instant.parse("2026-09-27T08:04:59Z")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5 minutes");
    }

    @Test
    void recordsTechnicalDistributionFailure() {
        UUID campaignId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(distributionService.dispatchManual(campaignId, actorId)).thenThrow(failure);

        assertThatThrownBy(() -> service.send(actorId, campaignId)).isSameAs(failure);

        verify(failureService).markFailed(campaignId, failure);
    }
}
