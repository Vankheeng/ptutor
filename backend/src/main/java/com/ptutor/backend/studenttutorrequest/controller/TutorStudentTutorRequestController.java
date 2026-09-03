package com.ptutor.backend.studenttutorrequest.controller;

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

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestResponse;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestStatusRequest;
import com.ptutor.backend.studenttutorrequest.service.StudentTutorRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/student-tutor-requests")
@RequiredArgsConstructor
@Validated
public class TutorStudentTutorRequestController {

    private final StudentTutorRequestService requestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentTutorRequestResponse>>> findIncoming(
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.findIncoming(currentUserProvider.getCurrentUserId(), status),
                "/api/v1/tutors/me/student-tutor-requests"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> findIncomingById(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.findIncomingById(currentUserProvider.getCurrentUserId(), requestId),
                "/api/v1/tutors/me/student-tutor-requests/" + requestId));
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> updateStatus(
            @PathVariable UUID requestId,
            @Valid @RequestBody StudentTutorRequestStatusRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.updateStatus(
                        currentUserProvider.getCurrentUserId(), requestId, request.status()),
                "/api/v1/tutors/me/student-tutor-requests/" + requestId + "/status"));
    }
}
