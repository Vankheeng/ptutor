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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.StudentTutorRequestCreateRequest;
import com.ptutor.backend.dto.request.StudentTutorRequestStatusRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudentTutorRequestMineResponse;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudentTutorRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
@Validated
public class StudentTutorRequestStudentController {

    private static final String BASE_PATH = "/api/v1/students/me/student-tutor-requests";

    private final StudentTutorRequestService studentTutorRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/teaching-requests/{teachingRequestId}/student-tutor-requests")
    public ResponseEntity<ApiResponse<StudentTutorRequestMineResponse>> create(
            @PathVariable UUID teachingRequestId,
            @Valid @RequestBody StudentTutorRequestCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                studentTutorRequestService.createForStudent(
                        currentUserProvider.getCurrentUserId(), teachingRequestId, request),
                "/api/v1/students/me/teaching-requests/" + teachingRequestId + "/student-tutor-requests"));
    }

    @GetMapping("/student-tutor-requests")
    public ResponseEntity<ApiResponse<PageResponse<StudentTutorRequestMineResponse>>> findMine(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                studentTutorRequestService.findMineForStudent(
                        currentUserProvider.getCurrentUserId(), status, pageable),
                BASE_PATH));
    }

    @PatchMapping("/student-tutor-requests/{requestId}/status")
    public ResponseEntity<ApiResponse<StudentTutorRequestMineResponse>> cancel(
            @PathVariable UUID requestId,
            @Valid @RequestBody StudentTutorRequestStatusRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "STUDENT_TUTOR_REQUEST_CANCELLED",
                "Student tutor request cancelled successfully",
                studentTutorRequestService.cancelForStudent(
                        currentUserProvider.getCurrentUserId(), requestId, request.status()),
                BASE_PATH + "/" + requestId + "/status"));
    }
}
