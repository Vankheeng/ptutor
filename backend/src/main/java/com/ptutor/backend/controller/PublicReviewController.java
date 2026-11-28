package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.dto.response.TutorReviewOverviewResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.ReviewService;

import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Validated
public class PublicReviewController {

    private final ReviewService reviewService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicReviewResponse>>> findLatest(
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(responseFactory.success(
                reviewService.findLatestPublicReviews(limit), "/api/v1/reviews"));
    }

    @GetMapping("/tutors/{tutorId}")
    public ResponseEntity<ApiResponse<TutorReviewOverviewResponse>> findByTutor(
            @PathVariable UUID tutorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int size) {
        String path = "/api/v1/reviews/tutors/" + tutorId;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                reviewService.findTutorReviewOverview(tutorId, pageable), path));
    }
}
