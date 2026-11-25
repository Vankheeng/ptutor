package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.CreateNotificationCampaignRequest;
import com.ptutor.backend.dto.request.UpdateNotificationCampaignRequest;
import com.ptutor.backend.dto.response.NotificationCampaignResponse;
import com.ptutor.backend.dto.response.NotificationCampaignStatisticsResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.entity.enums.NotificationRecordType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationCampaignService {

    private static final long MINIMUM_SCHEDULE_LEAD_SECONDS = 300;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationCampaignDistributionService distributionService;
    private final NotificationCampaignFailureService failureService;
    private final Clock clock;

    @Transactional
    public NotificationCampaignResponse create(UUID actorUserId, CreateNotificationCampaignRequest request) {
        User actor = requireActiveActor(actorUserId);
        Notification campaign = Notification.builder()
                .recordType(NotificationRecordType.BROADCAST_MASTER)
                .title(request.title().strip())
                .content(request.content().strip())
                .type(NotificationType.SYSTEM)
                .isRead(false)
                .category(request.category())
                .audience(request.audience())
                .campaignStatus(NotificationCampaignStatus.DRAFT)
                .createdByUser(actor)
                .updatedByUser(actor)
                .recipientCount(0)
                .build();
        return toResponse(notificationRepository.saveAndFlush(campaign));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationCampaignResponse> findAll(
            NotificationCampaignStatus status,
            NotificationAudience audience,
            NotificationCategory category,
            String keyword,
            Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<Notification> campaigns = notificationRepository.findCampaigns(
                status, audience, category, normalizedKeyword, pageable);
        return PageResponse.from(campaigns, campaigns.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public NotificationCampaignResponse findById(UUID campaignId) {
        return toResponse(findCampaign(campaignId));
    }

    @Transactional
    public NotificationCampaignResponse update(
            UUID actorUserId, UUID campaignId, UpdateNotificationCampaignRequest request) {
        User actor = requireActiveActor(actorUserId);
        Notification campaign = findCampaignForUpdate(campaignId);
        NotificationCampaignStatus status = campaign.getCampaignStatus();
        if (status == NotificationCampaignStatus.PROCESSING
                || status == NotificationCampaignStatus.CANCELLED
                || status == NotificationCampaignStatus.RETRACTED) {
            throw invalidTransition("This notification cannot be updated in status " + status);
        }
        if (status == NotificationCampaignStatus.SENT && request.audience() != campaign.getAudience()) {
            throw invalidTransition("The audience of a sent notification cannot be changed");
        }

        campaign.setTitle(request.title().strip());
        campaign.setContent(request.content().strip());
        campaign.setCategory(request.category());
        campaign.setAudience(request.audience());
        campaign.setUpdatedByUser(actor);
        Notification saved = notificationRepository.saveAndFlush(campaign);
        if (status == NotificationCampaignStatus.SENT) {
            notificationRepository.updateCampaignDeliveries(
                    campaignId, saved.getTitle(), saved.getContent(), saved.getCategory());
            saved = findCampaign(campaignId);
        }
        return toResponse(saved);
    }

    @Transactional
    public NotificationCampaignResponse schedule(UUID actorUserId, UUID campaignId, Instant scheduledAt) {
        User actor = requireActiveActor(actorUserId);
        Notification campaign = findCampaignForUpdate(campaignId);
        if (campaign.getCampaignStatus() != NotificationCampaignStatus.DRAFT
                && campaign.getCampaignStatus() != NotificationCampaignStatus.SCHEDULED
                && campaign.getCampaignStatus() != NotificationCampaignStatus.FAILED) {
            throw invalidTransition("Only a draft, failed, or scheduled notification can be scheduled");
        }
        Instant minimum = clock.instant().plusSeconds(MINIMUM_SCHEDULE_LEAD_SECONDS);
        if (scheduledAt.isBefore(minimum)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_SCHEDULE",
                    "Scheduled time must be at least 5 minutes in the future");
        }
        campaign.setScheduledAt(LocalDateTime.ofInstant(scheduledAt, ZoneOffset.UTC));
        campaign.setCampaignStatus(NotificationCampaignStatus.SCHEDULED);
        campaign.setUpdatedByUser(actor);
        campaign.setFailureReason(null);
        return toResponse(notificationRepository.saveAndFlush(campaign));
    }

    @Transactional
    public NotificationCampaignResponse cancelSchedule(UUID actorUserId, UUID campaignId) {
        User actor = requireActiveActor(actorUserId);
        Notification campaign = findCampaignForUpdate(campaignId);
        if (campaign.getCampaignStatus() != NotificationCampaignStatus.SCHEDULED) {
            throw invalidTransition("Only a scheduled notification can have its schedule cancelled");
        }
        campaign.setCampaignStatus(NotificationCampaignStatus.DRAFT);
        campaign.setScheduledAt(null);
        campaign.setUpdatedByUser(actor);
        return toResponse(notificationRepository.saveAndFlush(campaign));
    }

    public NotificationCampaignResponse send(UUID actorUserId, UUID campaignId) {
        requireActiveActor(actorUserId);
        try {
            return distributionService.dispatchManual(campaignId, actorUserId);
        } catch (ApiException businessFailure) {
            throw businessFailure;
        } catch (RuntimeException technicalFailure) {
            failureService.markFailed(campaignId, technicalFailure);
            throw technicalFailure;
        }
    }

    @Transactional
    public NotificationCampaignResponse delete(UUID actorUserId, UUID campaignId) {
        User actor = requireActiveActor(actorUserId);
        Notification campaign = findCampaignForUpdate(campaignId);
        NotificationCampaignStatus status = campaign.getCampaignStatus();
        if (status == NotificationCampaignStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_IS_PROCESSING",
                    "A notification being distributed cannot be deleted");
        }
        if (status == NotificationCampaignStatus.CANCELLED || status == NotificationCampaignStatus.RETRACTED) {
            return toResponse(campaign);
        }
        if (status == NotificationCampaignStatus.SENT) {
            notificationRepository.retractCampaignDeliveries(campaignId);
            campaign.setCampaignStatus(NotificationCampaignStatus.RETRACTED);
        } else {
            campaign.setCampaignStatus(NotificationCampaignStatus.CANCELLED);
        }
        campaign.setScheduledAt(null);
        campaign.setUpdatedByUser(actor);
        return toResponse(notificationRepository.saveAndFlush(campaign));
    }

    @Transactional(readOnly = true)
    public NotificationCampaignStatisticsResponse statistics(UUID campaignId) {
        Notification campaign = findCampaign(campaignId);
        boolean delivered = campaign.getCampaignStatus() == NotificationCampaignStatus.SENT;
        long read = delivered ? notificationRepository.countByParentNotification_IdAndIsRead(campaignId, true) : 0;
        long unread = delivered ? Math.max(0, campaign.getRecipientCount() - read) : 0;
        return new NotificationCampaignStatisticsResponse(
                campaignId, campaign.getRecipientCount(), read, unread);
    }

    private User requireActiveActor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOTIFICATION_MANAGER_REQUIRED",
                        "An active administrator or authorized employee is required"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOTIFICATION_MANAGER_REQUIRED",
                    "An active administrator or authorized employee is required");
        }
        return user;
    }

    private Notification findCampaign(UUID campaignId) {
        return notificationRepository.findByIdAndRecordType(campaignId, NotificationRecordType.BROADCAST_MASTER)
                .orElseThrow(() -> notFound(campaignId));
    }

    private Notification findCampaignForUpdate(UUID campaignId) {
        return notificationRepository.findCampaignByIdForUpdate(campaignId)
                .orElseThrow(() -> notFound(campaignId));
    }

    private NotificationCampaignResponse toResponse(Notification campaign) {
        long readCount = campaign.getCampaignStatus() == NotificationCampaignStatus.SENT
                ? notificationRepository.countByParentNotification_IdAndIsRead(campaign.getId(), true) : 0;
        int recipients = campaign.getRecipientCount() == null ? 0 : campaign.getRecipientCount();
        long unreadCount = campaign.getCampaignStatus() == NotificationCampaignStatus.SENT
                ? Math.max(0, recipients - readCount) : 0;
        return new NotificationCampaignResponse(
                campaign.getId(), campaign.getTitle(), campaign.getContent(), campaign.getCategory(),
                campaign.getAudience(), campaign.getCampaignStatus(), instant(campaign.getScheduledAt()),
                instant(campaign.getProcessingStartedAt()), instant(campaign.getSentAt()), recipients,
                readCount, unreadCount, campaign.getFailureReason(),
                idOf(campaign.getCreatedByUser()), idOf(campaign.getUpdatedByUser()),
                instant(campaign.getCreatedAt()), instant(campaign.getUpdatedAt()));
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private ApiException notFound(UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_CAMPAIGN_NOT_FOUND",
                "Notification campaign not found: " + id);
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_NOTIFICATION_STATUS_TRANSITION", message);
    }
}
