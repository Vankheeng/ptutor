package com.ptutor.backend.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.service.NotificationCampaignDistributionService;
import com.ptutor.backend.service.NotificationCampaignFailureService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationCampaignScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationCampaignScheduler.class);
    private static final int CAMPAIGNS_PER_RUN = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationCampaignDistributionService distributionService;
    private final NotificationCampaignFailureService failureService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval-ms:60000}")
    public void dispatchDueCampaigns() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (UUID campaignId : notificationRepository.findDueCampaignIds(
                now, PageRequest.of(0, CAMPAIGNS_PER_RUN))) {
            try {
                distributionService.dispatchScheduled(campaignId);
            } catch (RuntimeException failure) {
                LOGGER.error("Scheduled notification campaign {} failed", campaignId, failure);
                failureService.markFailed(campaignId, failure);
            }
        }
    }
}
