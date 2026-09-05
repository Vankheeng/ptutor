package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.StudyingRequestRequest;
import com.ptutor.backend.dto.request.StudyingRequestStatusRequest;
import com.ptutor.backend.dto.request.StudyingRequestUpdateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudyingRequestResponse;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudyingRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/studying-requests")
@RequiredArgsConstructor
@Validated
public class StudyingRequestController {

    private static final String BASE_PATH = "/api/v1/students/me/studying-requests";

    private final StudyingRequestService studyingRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<StudyingRequestResponse>> create(
            @Valid @RequestBody StudyingRequestRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                studyingRequestService.create(currentUserProvider.getCurrentUserId(), request),
                BASE_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StudyingRequestResponse>>> findMine(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                studyingRequestService.findMine(currentUserProvider.getCurrentUserId(), status, pageable),
                BASE_PATH));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<StudyingRequestResponse>> findMineById(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                studyingRequestService.findMineById(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<ApiResponse<StudyingRequestResponse>> update(
            @PathVariable UUID requestId,
            @Valid @RequestBody StudyingRequestUpdateRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                studyingRequestService.update(currentUserProvider.getCurrentUserId(), requestId, request),
                BASE_PATH + "/" + requestId));
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<ApiResponse<StudyingRequestResponse>> updateStatus(
            @PathVariable UUID requestId,
            @Valid @RequestBody StudyingRequestStatusRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                studyingRequestService.updateStatus(
                        currentUserProvider.getCurrentUserId(), requestId, request.status()),
                BASE_PATH + "/" + requestId + "/status"));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<StudyingRequestResponse>> cancel(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                "STUDYING_REQUEST_CANCELLED",
                "Studying request cancelled successfully",
                studyingRequestService.cancel(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/cancel"));
    }
}
