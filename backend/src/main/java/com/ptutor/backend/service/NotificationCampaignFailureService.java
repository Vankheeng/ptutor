package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationCampaignFailureService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID campaignId, RuntimeException failure) {
        notificationRepository.findCampaignByIdForUpdate(campaignId).ifPresent(campaign -> {
            NotificationCampaignStatus status = campaign.getCampaignStatus();
            if (status == NotificationCampaignStatus.DRAFT
                    || status == NotificationCampaignStatus.SCHEDULED
                    || status == NotificationCampaignStatus.FAILED) {
                campaign.setCampaignStatus(NotificationCampaignStatus.FAILED);
                campaign.setFailureReason(safeFailureMessage(failure));
                notificationRepository.saveAndFlush(campaign);
            }
        });
    }

    private String safeFailureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
