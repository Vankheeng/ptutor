package com.ptutor.backend.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.SupportRequestAnswerRequest;
import com.ptutor.backend.dto.response.AdminSupportRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminSupportRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.SupportRequestStatsResponse;
import com.ptutor.backend.entity.enums.SupportRequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.SupportRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Hàng chờ yêu cầu hỗ trợ cho ADMIN và nhân viên CUSTOMER_SERVICE (docs/support-chatbot/14-yeu-cau-ho-tro.md
 * mục 14.5). Chức năng nhân viên được kiểm ở {@code EmployeeFunctionAuthorizationFilter}.
 */
@RestController
@RequestMapping("/api/v1/admin/support-requests")
@RequiredArgsConstructor
@Validated
public class AdminSupportRequestController {

    private static final String BASE_PATH = "/api/v1/admin/support-requests";

    private final SupportRequestService supportRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminSupportRequestSummaryResponse>>> findQueue(
            @RequestParam(required = false) SupportRequestStatus status,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(responseFactory.success(supportRequestService.findQueue(
                currentUserProvider.getCurrentUserId(), status, assignedToMe, role, from, to, pageable), BASE_PATH));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<SupportRequestStatsResponse>> stats() {
        return ResponseEntity.ok(responseFactory.success(supportRequestService.stats(), BASE_PATH + "/stats"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<AdminSupportRequestDetailResponse>> findById(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.findForStaff(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId));
    }

    @PatchMapping("/{requestId}/assign")
    public ResponseEntity<ApiResponse<AdminSupportRequestDetailResponse>> assign(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.assign(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/assign"));
    }

    @PatchMapping("/{requestId}/release")
    public ResponseEntity<ApiResponse<AdminSupportRequestDetailResponse>> release(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.release(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/release"));
    }

    @PatchMapping("/{requestId}/answer")
    public ResponseEntity<ApiResponse<AdminSupportRequestDetailResponse>> answer(
            @PathVariable UUID requestId, @Valid @RequestBody SupportRequestAnswerRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.answer(currentUserProvider.getCurrentUserId(), requestId, request),
                BASE_PATH + "/" + requestId + "/answer"));
    }
}
