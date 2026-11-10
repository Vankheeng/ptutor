package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.TutorRefundResponseRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.RefundRequestResponse;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.RefundRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/refund-requests")
@RequiredArgsConstructor
@Validated
public class TutorRefundRequestController {

    private static final String BASE_PATH = "/api/v1/tutors/me/refund-requests";

    private final RefundRequestService refundRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundRequestResponse>>> findAll(
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.findForTutor(currentUserProvider.getCurrentUserId(), status, pageable),
                BASE_PATH));
    }

    @GetMapping("/{refundRequestId}")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> findById(@PathVariable UUID refundRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.findForTutorById(currentUserProvider.getCurrentUserId(), refundRequestId),
                BASE_PATH + "/" + refundRequestId));
    }

    @PostMapping("/{refundRequestId}/response")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> respond(
            @PathVariable UUID refundRequestId,
            @Valid @RequestBody TutorRefundResponseRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refundRequestService.respond(currentUserProvider.getCurrentUserId(), refundRequestId, request),
                BASE_PATH + "/" + refundRequestId + "/response"));
    }
}
