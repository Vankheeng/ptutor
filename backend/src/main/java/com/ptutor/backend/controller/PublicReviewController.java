package com.ptutor.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.ReviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicReviewResponse>>> findLatest(
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(responseFactory.success(
                reviewService.findLatestPublicReviews(limit), "/api/v1/reviews"));
    }
}
