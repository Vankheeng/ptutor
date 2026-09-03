package com.ptutor.backend.studenttutorrequest.controller;

import java.util.List;
import java.util.UUID;

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

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestCreateRequest;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestResponse;
import com.ptutor.backend.studenttutorrequest.service.StudentTutorRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/tutor-requests")
@RequiredArgsConstructor
@Validated
public class StudentTutorRequestController {

    private final StudentTutorRequestService requestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> create(
            @Valid @RequestBody StudentTutorRequestCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                requestService.create(currentUserProvider.getCurrentUserId(), request),
                "/api/v1/students/me/tutor-requests"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentTutorRequestResponse>>> findMine(
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.findMine(currentUserProvider.getCurrentUserId(), status),
                "/api/v1/students/me/tutor-requests"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> findMineById(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.findMineById(currentUserProvider.getCurrentUserId(), requestId),
                "/api/v1/students/me/tutor-requests/" + requestId));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<StudentTutorRequestResponse>> cancel(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                requestService.cancel(currentUserProvider.getCurrentUserId(), requestId),
                "/api/v1/students/me/tutor-requests/" + requestId + "/cancel"));
    }
}
