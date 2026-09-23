package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.enums.NotificationReadStatus;
import com.ptutor.backend.dto.response.NotificationReadAllResponse;
import com.ptutor.backend.dto.response.NotificationResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.UnreadCountResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.NotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> findMine(
            @RequestParam(required = false) NotificationReadStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responseFactory.success(notificationService.findMine(
                currentUserProvider.getCurrentUserId(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                "/api/v1/users/me/notifications"));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID notificationId) {
        String path = "/api/v1/users/me/notifications/" + notificationId + "/read";
        return ResponseEntity.ok(responseFactory.success(notificationService.markRead(
                currentUserProvider.getCurrentUserId(), notificationId), path));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<NotificationReadAllResponse>> markAllRead() {
        return ResponseEntity.ok(responseFactory.success(notificationService.markAllRead(
                currentUserProvider.getCurrentUserId()), "/api/v1/users/me/notifications/read-all"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount() {
        return ResponseEntity.ok(responseFactory.success(notificationService.unreadCount(
                currentUserProvider.getCurrentUserId()), "/api/v1/users/me/notifications/unread-count"));
    }
}
