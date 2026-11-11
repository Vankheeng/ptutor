package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ComplaintReassignmentRequest;
import com.ptutor.backend.dto.request.RefundApprovalRequest;
import com.ptutor.backend.dto.request.RefundRejectionRequest;
import com.ptutor.backend.dto.response.AdminRefundRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminRefundRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminRefundRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/refund-requests")
@RequiredArgsConstructor
@Validated
public class AdminRefundRequestController {

    private static final String BASE_PATH = "/api/v1/admin/refund-requests";

    private final AdminRefundRequestService refundRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminRefundRequestSummaryResponse>>> findAll(
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.findAll(status, contractId, keyword, pageable), BASE_PATH));
    }

    @GetMapping("/{refundRequestId}")
    public ResponseEntity<ApiResponse<AdminRefundRequestDetailResponse>> findById(@PathVariable UUID refundRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.findById(refundRequestId), BASE_PATH + "/" + refundRequestId));
    }

    @PatchMapping("/{refundRequestId}/start-review")
    public ResponseEntity<ApiResponse<AdminRefundRequestDetailResponse>> startReview(
            @PathVariable UUID refundRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.startReview(currentUserProvider.getCurrentUserId(), refundRequestId),
                BASE_PATH + "/" + refundRequestId + "/start-review"));
    }

    @PatchMapping("/{refundRequestId}/approve")
    public ResponseEntity<ApiResponse<AdminRefundRequestDetailResponse>> approve(
            @PathVariable UUID refundRequestId,
            @Valid @RequestBody RefundApprovalRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.approve(currentUserProvider.getCurrentUserId(), refundRequestId, request),
                BASE_PATH + "/" + refundRequestId + "/approve"));
    }

    @PatchMapping("/{refundRequestId}/reject")
    public ResponseEntity<ApiResponse<AdminRefundRequestDetailResponse>> reject(
            @PathVariable UUID refundRequestId,
            @Valid @RequestBody RefundRejectionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.reject(currentUserProvider.getCurrentUserId(), refundRequestId, request.reason()),
                BASE_PATH + "/" + refundRequestId + "/reject"));
    }

    @PatchMapping("/{refundRequestId}/reassign")
    public ResponseEntity<ApiResponse<AdminRefundRequestDetailResponse>> reassign(
            @PathVariable UUID refundRequestId,
            @Valid @RequestBody ComplaintReassignmentRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.reassign(
                        currentUserProvider.getCurrentUserId(), refundRequestId, request.employeeId()),
                BASE_PATH + "/" + refundRequestId + "/reassign"));
    }
}
