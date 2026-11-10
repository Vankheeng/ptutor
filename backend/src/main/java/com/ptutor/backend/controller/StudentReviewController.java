package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ReviewCreateRequest;
import com.ptutor.backend.dto.response.ReviewResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/contracts/{contractId}/review")
@RequiredArgsConstructor
@Validated
public class StudentReviewController {

    private final ReviewService reviewService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @PathVariable UUID contractId,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                "REVIEW_CREATED",
                "Review created",
                reviewService.createForContract(currentUserProvider.getCurrentUserId(), contractId, request),
                "/api/v1/students/me/contracts/" + contractId + "/review"));
    }
}
