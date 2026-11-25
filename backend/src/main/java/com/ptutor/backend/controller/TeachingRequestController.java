package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.dto.request.TeachingRequestRequest;
import com.ptutor.backend.dto.request.TeachingRequestStatusRequest;
import com.ptutor.backend.dto.response.TeachingRequestResponse;
import com.ptutor.backend.service.TeachingRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/teaching-requests")
@RequiredArgsConstructor
@Validated
public class TeachingRequestController {

    private final TeachingRequestService teachingRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> create(
            @Valid @RequestBody TeachingRequestRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                teachingRequestService.create(currentUserProvider.getCurrentUserId(), request),
                "/api/v1/tutors/me/teaching-requests"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeachingRequestResponse>>> findMine(
            @RequestParam(required = false) RequestStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.findMine(currentUserProvider.getCurrentUserId(), status),
                "/api/v1/tutors/me/teaching-requests"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> findMineById(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.findMineById(currentUserProvider.getCurrentUserId(), requestId),
                "/api/v1/tutors/me/teaching-requests/" + requestId));
    }

    @PutMapping("/{requestId}")
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> update(
            @PathVariable UUID requestId,
            @Valid @RequestBody TeachingRequestRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.update(currentUserProvider.getCurrentUserId(), requestId, request),
                "/api/v1/tutors/me/teaching-requests/" + requestId));
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> updateStatus(
            @PathVariable UUID requestId,
            @Valid @RequestBody TeachingRequestStatusRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.updateStatus(
                        currentUserProvider.getCurrentUserId(), requestId, request.status()),
                "/api/v1/tutors/me/teaching-requests/" + requestId + "/status"));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> cancel(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success("TEACHING_REQUEST_CANCELLED",
                "Teaching request cancelled successfully",
                teachingRequestService.cancel(currentUserProvider.getCurrentUserId(), requestId),
                "/api/v1/tutors/me/teaching-requests/" + requestId + "/cancel"));
    }
}
