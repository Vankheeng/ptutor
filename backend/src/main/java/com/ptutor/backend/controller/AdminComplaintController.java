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

import com.ptutor.backend.dto.request.ComplaintEvidenceRequest;
import com.ptutor.backend.dto.request.ComplaintReassignmentRequest;
import com.ptutor.backend.dto.request.ComplaintResolutionRequest;
import com.ptutor.backend.dto.response.AdminComplaintDetailResponse;
import com.ptutor.backend.dto.response.AdminComplaintSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminComplaintService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/complaints")
@RequiredArgsConstructor
@Validated
public class AdminComplaintController {

    private static final String BASE_PATH = "/api/v1/admin/complaints";

    private final AdminComplaintService complaintService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminComplaintSummaryResponse>>> findAll(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findAll(status, contractId, keyword, pageable), BASE_PATH));
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> findById(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findById(complaintId), BASE_PATH + "/" + complaintId));
    }

    @PatchMapping("/{complaintId}/start-review")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> startReview(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.startReview(currentUserProvider.getCurrentUserId(), complaintId),
                BASE_PATH + "/" + complaintId + "/start-review"));
    }

    @PatchMapping("/{complaintId}/request-evidence")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> requestEvidence(
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintEvidenceRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.requestEvidence(
                        currentUserProvider.getCurrentUserId(), complaintId, request.message()),
                BASE_PATH + "/" + complaintId + "/request-evidence"));
    }

    @PatchMapping("/{complaintId}/reassign")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> reassign(
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintReassignmentRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.reassign(
                        currentUserProvider.getCurrentUserId(), complaintId, request.employeeId()),
                BASE_PATH + "/" + complaintId + "/reassign"));
    }

    @PatchMapping("/{complaintId}/accept")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> accept(
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintResolutionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.accept(currentUserProvider.getCurrentUserId(), complaintId, request.resolution()),
                BASE_PATH + "/" + complaintId + "/accept"));
    }

    @PatchMapping("/{complaintId}/reject")
    public ResponseEntity<ApiResponse<AdminComplaintDetailResponse>> reject(
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintResolutionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.reject(currentUserProvider.getCurrentUserId(), complaintId, request.resolution()),
                BASE_PATH + "/" + complaintId + "/reject"));
    }
}
