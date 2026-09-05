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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.ComplaintUpdateRequest;
import com.ptutor.backend.dto.response.ComplaintResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ComplaintService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/complaints")
@RequiredArgsConstructor
@Validated
public class ComplaintController {

    private static final String BASE_PATH = "/api/v1/users/me/complaints";

    private final ComplaintService complaintService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintResponse>> create(
            @Valid @RequestBody ComplaintCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                complaintService.create(currentUserProvider.getCurrentUserId(), request), BASE_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ComplaintResponse>>> findMine(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findMine(currentUserProvider.getCurrentUserId(), status, pageable), BASE_PATH));
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> findMineById(@PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findMineById(currentUserProvider.getCurrentUserId(), complaintId),
                BASE_PATH + "/" + complaintId));
    }

    @PutMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> update(
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintUpdateRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.update(currentUserProvider.getCurrentUserId(), complaintId, request),
                BASE_PATH + "/" + complaintId));
    }

    @PostMapping("/{complaintId}/cancel")
    public ResponseEntity<ApiResponse<ComplaintResponse>> cancel(@PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.cancel(currentUserProvider.getCurrentUserId(), complaintId),
                BASE_PATH + "/" + complaintId + "/cancel"));
    }
}
