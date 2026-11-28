package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.response.StudentRecommendationResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudentRecommendationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/student-recommendations")
@RequiredArgsConstructor
@Validated
public class TutorStudentRecommendationController {

    private static final String PATH = "/api/v1/tutors/me/student-recommendations";

    private final StudentRecommendationService studentRecommendationService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Without {@code logId}: runs a new search from the body. With {@code logId}: returns another
     * page of that earlier search (the body is ignored), in the order computed back then.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StudentRecommendationResponse>> recommend(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID logId,
            @Valid @RequestBody(required = false) StudentRecommendationRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                studentRecommendationService.recommendStudents(
                        currentUserProvider.getCurrentUserId(), request, page, size, logId),
                PATH));
    }
}
