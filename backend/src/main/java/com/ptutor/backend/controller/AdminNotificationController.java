package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.CreateNotificationCampaignRequest;
import com.ptutor.backend.dto.request.ScheduleNotificationCampaignRequest;
import com.ptutor.backend.dto.request.UpdateNotificationCampaignRequest;
import com.ptutor.backend.dto.response.NotificationCampaignResponse;
import com.ptutor.backend.dto.response.NotificationCampaignStatisticsResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.NotificationCampaignService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Validated
public class AdminNotificationController {

    private static final String BASE_PATH = "/api/v1/admin/notifications";

    private final NotificationCampaignService campaignService;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> create(
            @Valid @RequestBody CreateNotificationCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success(
                "NOTIFICATION_CREATED", "Notification draft created successfully",
                campaignService.create(currentUserProvider.getCurrentUserId(), request), BASE_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationCampaignResponse>>> findAll(
            @RequestParam(required = false) NotificationCampaignStatus status,
            @RequestParam(required = false) NotificationAudience audience,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responseFactory.success(campaignService.findAll(
                status, audience, category, keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), BASE_PATH));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> findById(
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                campaignService.findById(notificationId), path(notificationId)));
    }

    @PutMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> update(
            @PathVariable UUID notificationId,
            @Valid @RequestBody UpdateNotificationCampaignRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_UPDATED", "Notification updated successfully",
                campaignService.update(currentUserProvider.getCurrentUserId(), notificationId, request),
                path(notificationId)));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> delete(
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_REMOVED", "Notification cancelled or retracted successfully",
                campaignService.delete(currentUserProvider.getCurrentUserId(), notificationId),
                path(notificationId)));
    }

    @PostMapping("/{notificationId}/send")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> send(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_SENT", "Notification sent successfully",
                campaignService.send(currentUserProvider.getCurrentUserId(), notificationId),
                path(notificationId) + "/send"));
    }

    @PostMapping("/{notificationId}/schedule")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> schedule(
            @PathVariable UUID notificationId,
            @Valid @RequestBody ScheduleNotificationCampaignRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_SCHEDULED", "Notification scheduled successfully",
                campaignService.schedule(currentUserProvider.getCurrentUserId(), notificationId, request.scheduledAt()),
                path(notificationId) + "/schedule"));
    }

    @PostMapping("/{notificationId}/cancel-schedule")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> cancelSchedule(
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_SCHEDULE_CANCELLED", "Notification schedule cancelled successfully",
                campaignService.cancelSchedule(currentUserProvider.getCurrentUserId(), notificationId),
                path(notificationId) + "/cancel-schedule"));
    }

    @PostMapping("/{notificationId}/retry")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> retry(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                "NOTIFICATION_SENT", "Notification sent successfully",
                campaignService.send(currentUserProvider.getCurrentUserId(), notificationId),
                path(notificationId) + "/retry"));
    }

    @GetMapping("/{notificationId}/statistics")
    public ResponseEntity<ApiResponse<NotificationCampaignStatisticsResponse>> statistics(
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(responseFactory.success(
                campaignService.statistics(notificationId), path(notificationId) + "/statistics"));
    }

    private String path(UUID notificationId) {
        return BASE_PATH + "/" + notificationId;
    }
}
