package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.SupportRequestCreateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.SupportRequestResponse;
import com.ptutor.backend.entity.enums.SupportRequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AssistantProxyService;
import com.ptutor.backend.service.SupportRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Yêu cầu hỗ trợ của chính người dùng (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5). Phân quyền ở
 * {@code SecurityConstants.SUPPORT_REQUEST_SELF_SERVICE_API}: chỉ STUDENT, TUTOR.
 */
@RestController
@RequestMapping("/api/v1/users/me/support-requests")
@RequiredArgsConstructor
@Validated
public class SupportRequestController {

    private static final String BASE_PATH = "/api/v1/users/me/support-requests";

    private final SupportRequestService supportRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<SupportRequestResponse>> create(
            @Valid @RequestBody SupportRequestCreateRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String traceId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        // Không chuyển tiếp token: lấy đoạn trích chỉ cần danh tính người dùng, không gọi tool tra cứu.
        AssistantProxyService.Caller caller = new AssistantProxyService.Caller(
                currentUserProvider.getCurrentUserId(), currentUserProvider.getCurrentUserRole().name(), null, traceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success(
                supportRequestService.create(caller, request), BASE_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupportRequestResponse>>> findMine(
            @RequestParam(required = false) SupportRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.findMine(currentUserProvider.getCurrentUserId(), status, pageable), BASE_PATH));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> findById(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.findMine(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> cancel(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.cancel(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/cancel"));
    }

    @PostMapping("/{requestId}/close")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> close(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                supportRequestService.close(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/close"));
    }
}
