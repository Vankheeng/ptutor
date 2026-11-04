package com.ptutor.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.service.SupportRequestService;

import lombok.RequiredArgsConstructor;

/**
 * Job hằng đêm của yêu cầu hỗ trợ (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.3, 14.8): tự đóng yêu cầu đã
 * trả lời quá 7 ngày; xoá đoạn hội thoại đính kèm 365 ngày sau khi đóng.
 */
@Component
@RequiredArgsConstructor
public class SupportRequestScheduler {

    private final SupportRequestService supportRequestService;

    @Scheduled(cron = "${app.support-request.maintenance-cron:0 30 2 * * *}")
    public void nightlyMaintenance() {
        supportRequestService.autoCloseAnswered();
        supportRequestService.purgeOldExcerpts();
    }
}
