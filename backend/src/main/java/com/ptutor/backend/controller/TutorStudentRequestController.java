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

import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TutorStudentRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me")
@RequiredArgsConstructor
@Validated
public class TutorStudentRequestController {

    private static final String BASE_PATH = "/api/v1/tutors/me/tutor-student-requests";

    private final TutorStudentRequestService tutorStudentRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/studying-requests/{studyingRequestId}/tutor-student-requests")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> create(
            @PathVariable UUID studyingRequestId,
            @Valid @RequestBody TutorStudentRequestCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                tutorStudentRequestService.create(
                        currentUserProvider.getCurrentUserId(), studyingRequestId, request),
                "/api/v1/tutors/me/studying-requests/" + studyingRequestId + "/tutor-student-requests"));
    }

    @GetMapping("/tutor-student-requests")
    public ResponseEntity<ApiResponse<PageResponse<TutorStudentRequestResponse>>> findMine(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                tutorStudentRequestService.findMine(currentUserProvider.getCurrentUserId(), status, pageable),
                BASE_PATH));
    }

    @PostMapping("/tutor-student-requests/{requestId}/cancel")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> cancel(@PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                "TUTOR_STUDENT_REQUEST_CANCELLED",
                "Teaching proposal cancelled successfully",
                tutorStudentRequestService.cancel(currentUserProvider.getCurrentUserId(), requestId),
                BASE_PATH + "/" + requestId + "/cancel"));
    }
}
