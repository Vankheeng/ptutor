package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.NotificationCampaignResponse;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationRecordType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationCampaignDistributionService {

    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public NotificationCampaignResponse dispatchManual(UUID campaignId, UUID actorUserId) {
        return dispatch(campaignId, actorUserId, false);
    }

    @Transactional
    public void dispatchScheduled(UUID campaignId) {
        dispatch(campaignId, null, true);
    }

    private NotificationCampaignResponse dispatch(UUID campaignId, UUID actorUserId, boolean scheduled) {
        Notification campaign = notificationRepository.findCampaignByIdForUpdate(campaignId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_CAMPAIGN_NOT_FOUND",
                        "Notification campaign not found: " + campaignId));
        NotificationCampaignStatus status = campaign.getCampaignStatus();
        boolean valid = scheduled
                ? status == NotificationCampaignStatus.SCHEDULED
                : status == NotificationCampaignStatus.DRAFT || status == NotificationCampaignStatus.FAILED;
        if (!valid) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_NOTIFICATION_STATUS_TRANSITION",
                    "Notification cannot be sent in status " + status);
        }

        List<UUID> recipients = switch (campaign.getAudience()) {
            case STUDENT -> userRepository.findActiveStudentUserIds();
            case TUTOR -> userRepository.findActiveTutorUserIds();
            case ALL -> userRepository.findActiveStudentOrTutorUserIds();
        };
        if (recipients.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_ELIGIBLE_RECIPIENTS",
                    "No active recipient matches the selected audience");
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        campaign.setCampaignStatus(NotificationCampaignStatus.PROCESSING);
        campaign.setProcessingStartedAt(now);
        campaign.setFailureReason(null);
        if (actorUserId != null) {
            campaign.setUpdatedByUser(userReference(actorUserId));
        }
        notificationRepository.saveAndFlush(campaign);

        for (int start = 0; start < recipients.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, recipients.size());
            List<Notification> deliveries = new ArrayList<>(end - start);
            for (UUID recipientId : recipients.subList(start, end)) {
                deliveries.add(Notification.builder()
                        .recordType(NotificationRecordType.BROADCAST_DELIVERY)
                        .parentNotification(campaign)
                        .user(userReference(recipientId))
                        .title(campaign.getTitle())
                        .content(campaign.getContent())
                        .type(NotificationType.SYSTEM)
                        .category(campaign.getCategory())
                        .isRead(false)
                        .recipientCount(0)
                        .build());
            }
            notificationRepository.saveAll(deliveries);
            notificationRepository.flush();
        }

        campaign.setCampaignStatus(NotificationCampaignStatus.SENT);
        campaign.setSentAt(now);
        campaign.setScheduledAt(null);
        campaign.setRecipientCount(recipients.size());
        Notification saved = notificationRepository.saveAndFlush(campaign);
        return new NotificationCampaignResponse(
                saved.getId(), saved.getTitle(), saved.getContent(), saved.getCategory(), saved.getAudience(),
                saved.getCampaignStatus(), null, saved.getProcessingStartedAt().toInstant(ZoneOffset.UTC),
                saved.getSentAt().toInstant(ZoneOffset.UTC), saved.getRecipientCount(), 0,
                saved.getRecipientCount(), null, saved.getCreatedByUser().getId(),
                saved.getUpdatedByUser() == null ? null : saved.getUpdatedByUser().getId(),
                saved.getCreatedAt().toInstant(ZoneOffset.UTC), saved.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private User userReference(UUID userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
