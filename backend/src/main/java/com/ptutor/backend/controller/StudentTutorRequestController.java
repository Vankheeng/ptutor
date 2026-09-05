package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.StudentTutorRequestStatusRequest;
import com.ptutor.backend.dto.response.StudentTutorRequestResponse;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudentTutorRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests")
@RequiredArgsConstructor
@Validated
public class StudentTutorRequestController {

    private final StudentTutorRequestService studentTutorRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentTutorRequestResponse>>> findMine(
            @PathVariable UUID teachingRequestId,
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                studentTutorRequestService.findMine(
                        currentUserProvider.getCurrentUserId(), teachingRequestId, status),
                "/api/v1/tutors/me/teaching-requests/" + teachingRequestId + "/student-requests"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> findMineById(
            @PathVariable UUID teachingRequestId,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                studentTutorRequestService.findMineById(
                        currentUserProvider.getCurrentUserId(), teachingRequestId, requestId),
                "/api/v1/tutors/me/teaching-requests/" + teachingRequestId + "/student-requests/" + requestId));
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> updateStatus(
            @PathVariable UUID teachingRequestId,
            @PathVariable UUID requestId,
            @Valid @RequestBody StudentTutorRequestStatusRequest statusRequest) {
        return ResponseEntity.ok(responseFactory.success(
                studentTutorRequestService.updateStatus(
                        currentUserProvider.getCurrentUserId(),
                        teachingRequestId,
                        requestId,
                        statusRequest.status()),
                "/api/v1/tutors/me/teaching-requests/" + teachingRequestId
                        + "/student-requests/" + requestId + "/status"));
    }
}