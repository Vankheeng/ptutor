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

import com.ptutor.backend.dto.request.StudyingRequestRejectionRequest;
import com.ptutor.backend.dto.request.StudyingRequestApprovalRequest;
import com.ptutor.backend.dto.response.AdminStudyingRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminStudyingRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminStudyingRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/studying-requests")
@RequiredArgsConstructor
@Validated
public class AdminStudyingRequestController {

    private static final String BASE_PATH = "/api/v1/admin/studying-requests";

    private final AdminStudyingRequestService service;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminStudyingRequestSummaryResponse>>> findAll(
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

    @GetMapping("/{studyingRequestId}")
    public ResponseEntity<ApiResponse<AdminStudyingRequestDetailResponse>> findById(
            @PathVariable UUID studyingRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                service.findById(studyingRequestId), BASE_PATH + "/" + studyingRequestId));
    }

    @PatchMapping("/{studyingRequestId}/approve")
    public ResponseEntity<ApiResponse<AdminStudyingRequestDetailResponse>> approve(
            @PathVariable UUID studyingRequestId,
            @Valid @RequestBody StudyingRequestApprovalRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                service.approve(currentUserProvider.getCurrentUserId(), studyingRequestId, request),
                BASE_PATH + "/" + studyingRequestId + "/approve"));
    }

    @PatchMapping("/{studyingRequestId}/reject")
    public ResponseEntity<ApiResponse<AdminStudyingRequestDetailResponse>> reject(
            @PathVariable UUID studyingRequestId,
            @Valid @RequestBody StudyingRequestRejectionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                service.reject(currentUserProvider.getCurrentUserId(), studyingRequestId, request.rejectionReason()),
                BASE_PATH + "/" + studyingRequestId + "/reject"));
    }
}
