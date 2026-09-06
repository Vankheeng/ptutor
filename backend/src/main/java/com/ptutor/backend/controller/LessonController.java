package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.LessonRequest;
import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.LessonService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me")
@RequiredArgsConstructor
@Validated
public class LessonController {

    private static final String BASE_PATH = "/api/v1/tutors/me";

    private final LessonService lessonService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/contracts/{contractId}/lessons")
    public ResponseEntity<ApiResponse<LessonResponse>> create(
            @PathVariable UUID contractId,
            @Valid @RequestBody LessonRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                lessonService.create(currentUserProvider.getCurrentUserId(), contractId, request),
                BASE_PATH + "/contracts/" + contractId + "/lessons"));
    }

    @GetMapping("/contracts/{contractId}/lessons")
    public ResponseEntity<ApiResponse<PageResponse<LessonResponse>>> findByContract(
            @PathVariable UUID contractId,
            @RequestParam(required = false) LessonStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("date"), Sort.Order.asc("startTime")));
        return ResponseEntity.ok(responseFactory.success(
                lessonService.findByContract(currentUserProvider.getCurrentUserId(), contractId, status, pageable),
                BASE_PATH + "/contracts/" + contractId + "/lessons"));
    }

    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<LessonResponse>> findById(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(responseFactory.success(
                lessonService.findById(currentUserProvider.getCurrentUserId(), lessonId),
                BASE_PATH + "/lessons/" + lessonId));
    }

    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<LessonResponse>> update(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                lessonService.update(currentUserProvider.getCurrentUserId(), lessonId, request),
                BASE_PATH + "/lessons/" + lessonId));
    }

    @PatchMapping("/lessons/{lessonId}/status")
    public ResponseEntity<ApiResponse<LessonResponse>> updateStatus(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                lessonService.updateStatus(currentUserProvider.getCurrentUserId(), lessonId, request),
                BASE_PATH + "/lessons/" + lessonId + "/status"));
    }
}
