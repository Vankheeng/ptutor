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

import com.ptutor.backend.dto.request.TeachingRequestRejectionRequest;
import com.ptutor.backend.dto.request.TeachingRequestApprovalRequest;
import com.ptutor.backend.dto.response.AdminTeachingRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminTeachingRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminTeachingRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/teaching-requests")
@RequiredArgsConstructor
@Validated
public class AdminTeachingRequestController {

    private static final String BASE_PATH = "/api/v1/admin/teaching-requests";

    private final AdminTeachingRequestService service;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminTeachingRequestSummaryResponse>>> findAll(
            @RequestParam(defaultValue = "PENDING_REVIEW") RequestStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Sort.Direction direction = status == RequestStatus.PENDING_REVIEW
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                service.findAll(status, keyword, pageable), BASE_PATH));
    }

    @GetMapping("/{teachingRequestId}")
    public ResponseEntity<ApiResponse<AdminTeachingRequestDetailResponse>> findById(
            @PathVariable UUID teachingRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                service.findById(teachingRequestId), BASE_PATH + "/" + teachingRequestId));
    }

    @PatchMapping("/{teachingRequestId}/approve")
    public ResponseEntity<ApiResponse<AdminTeachingRequestDetailResponse>> approve(
            @PathVariable UUID teachingRequestId,
            @Valid @RequestBody(required = false) TeachingRequestApprovalRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                service.approve(currentUserProvider.getCurrentUserId(), teachingRequestId, request),
                BASE_PATH + "/" + teachingRequestId + "/approve"));
    }

    @PatchMapping("/{teachingRequestId}/reject")
    public ResponseEntity<ApiResponse<AdminTeachingRequestDetailResponse>> reject(
            @PathVariable UUID teachingRequestId,
            @Valid @RequestBody TeachingRequestRejectionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                service.reject(currentUserProvider.getCurrentUserId(), teachingRequestId, request.rejectionReason()),
                BASE_PATH + "/" + teachingRequestId + "/reject"));
    }
}
