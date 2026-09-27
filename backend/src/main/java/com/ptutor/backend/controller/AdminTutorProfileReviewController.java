package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.TutorProfileRejectionRequest;
import com.ptutor.backend.dto.response.AdminTutorDetailResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminTutorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/tutor-profiles")
@RequiredArgsConstructor
public class AdminTutorProfileReviewController {

    private static final String BASE_PATH = "/api/v1/admin/tutor-profiles";

    private final AdminTutorService adminTutorService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PatchMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<AdminTutorDetailResponse>> approve(@PathVariable UUID userId) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.verifyProfile(currentUserProvider.getCurrentUserId(), userId),
                BASE_PATH + "/" + userId + "/approve"));
    }

    @PatchMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<AdminTutorDetailResponse>> reject(
            @PathVariable UUID userId,
            @Valid @RequestBody TutorProfileRejectionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.rejectProfile(
                        currentUserProvider.getCurrentUserId(), userId, request.reason()),
                BASE_PATH + "/" + userId + "/reject"));
    }
}
