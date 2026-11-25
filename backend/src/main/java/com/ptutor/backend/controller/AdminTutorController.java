package com.ptutor.backend.controller;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.AdminTutorDetailResponse;
import com.ptutor.backend.dto.response.AdminTutorLessonResponse;
import com.ptutor.backend.dto.response.AdminTutorReviewResponse;
import com.ptutor.backend.dto.response.AdminTutorSummaryResponse;
import com.ptutor.backend.dto.response.AdminTutorTeachingHistoryResponse;
import com.ptutor.backend.dto.response.CertificateResponse;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorRecommendationScoreResponse;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminTutorService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/tutors")
@RequiredArgsConstructor
@Validated
public class AdminTutorController {

    private static final String BASE_PATH = "/api/v1/admin/tutors";
    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "recommendationScore", "averageRating");

    private final AdminTutorService adminTutorService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminTutorSummaryResponse>>> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus accountStatus,
            @RequestParam(required = false) TutorProfileStatus profileStatus,
            @RequestParam(required = false) BigDecimal minRecommendationScore,
            @RequestParam(required = false) BigDecimal maxRecommendationScore,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, sort(sortBy, sortDirection));
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findAll(
                        keyword, accountStatus, profileStatus,
                        minRecommendationScore, maxRecommendationScore, pageable),
                BASE_PATH));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminTutorDetailResponse>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findByUserId(userId), path(userId, "")));
    }

    @GetMapping("/{userId}/certificates")
    public ResponseEntity<ApiResponse<PageResponse<CertificateResponse>>> findCertificates(
            @PathVariable UUID userId,
            @RequestParam(required = false) CertificateStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findCertificates(userId, status, pageable), path(userId, "/certificates")));
    }

    @GetMapping("/{userId}/contracts")
    public ResponseEntity<ApiResponse<PageResponse<ContractResponse>>> findContracts(
            @PathVariable UUID userId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findContracts(userId, status, pageable), path(userId, "/contracts")));
    }

    @GetMapping("/{userId}/contracts/{contractId}")
    public ResponseEntity<ApiResponse<ContractResponse>> findContract(
            @PathVariable UUID userId, @PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findContract(userId, contractId), path(userId, "/contracts/" + contractId)));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<AdminTutorReviewResponse>>> findReviews(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findReviews(userId, rating, pageable), path(userId, "/reviews")));
    }

    @GetMapping("/{userId}/teaching-history")
    public ResponseEntity<ApiResponse<AdminTutorTeachingHistoryResponse>> findTeachingHistory(
            @PathVariable UUID userId,
            @RequestParam(required = false) LessonStatus lessonStatus,
            @RequestParam(required = false) ContractStatus contractStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("date"), Sort.Order.desc("startTime"), Sort.Order.desc("createdAt")));
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findTeachingHistory(
                        userId, lessonStatus, contractStatus, pageable),
                path(userId, "/teaching-history")));
    }

    @GetMapping("/{userId}/teaching-history/{lessonId}")
    public ResponseEntity<ApiResponse<AdminTutorLessonResponse>> findLesson(
            @PathVariable UUID userId, @PathVariable UUID lessonId) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findLesson(userId, lessonId),
                path(userId, "/teaching-history/" + lessonId)));
    }

    @GetMapping("/{userId}/score-details")
    public ResponseEntity<ApiResponse<TutorRecommendationScoreResponse>> findScoreDetails(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(responseFactory.success(
                adminTutorService.findScoreDetails(userId), path(userId, "/score-details")));
    }

    private Sort sort(String sortBy, String direction) {
        if (!ALLOWED_SORTS.contains(sortBy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TUTOR_SORT",
                    "Sort field must be one of: createdAt, recommendationScore, averageRating");
        }
        try {
            return Sort.by(Sort.Direction.fromString(direction), sortBy);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_DIRECTION",
                    "Sort direction must be ASC or DESC");
        }
    }

    private String path(UUID userId, String suffix) {
        return BASE_PATH + "/" + userId + suffix;
    }
}
