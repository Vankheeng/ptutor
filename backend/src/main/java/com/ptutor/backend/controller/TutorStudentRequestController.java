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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TutorStudentRequestService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests")
@RequiredArgsConstructor
@Validated
public class TutorStudentRequestController {

    private final TutorStudentRequestService tutorStudentRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TutorStudentRequestResponse>>> findMine(
            @PathVariable UUID studyingRequestId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                tutorStudentRequestService.findMine(
                        currentUserProvider.getCurrentUserId(), studyingRequestId, status, pageable),
                "/api/v1/students/me/studying-requests/" + studyingRequestId + "/tutor-requests"));
    }

    @GetMapping("/{tutorRequestId}")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> findMineById(
            @PathVariable UUID studyingRequestId,
            @PathVariable UUID tutorRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                tutorStudentRequestService.findMineById(
                        currentUserProvider.getCurrentUserId(), studyingRequestId, tutorRequestId),
                "/api/v1/students/me/studying-requests/" + studyingRequestId
                        + "/tutor-requests/" + tutorRequestId));
    }

    @PatchMapping("/{tutorRequestId}/accept")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> accept(
            @PathVariable UUID studyingRequestId,
            @PathVariable UUID tutorRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                "TUTOR_STUDENT_REQUEST_ACCEPTED",
                "Tutor request accepted successfully",
                tutorStudentRequestService.accept(
                        currentUserProvider.getCurrentUserId(), studyingRequestId, tutorRequestId),
                "/api/v1/students/me/studying-requests/" + studyingRequestId
                        + "/tutor-requests/" + tutorRequestId + "/accept"));
    }

    @PatchMapping("/{tutorRequestId}/reject")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> reject(
            @PathVariable UUID studyingRequestId,
            @PathVariable UUID tutorRequestId) {
        return ResponseEntity.ok(responseFactory.success(
                "TUTOR_STUDENT_REQUEST_REJECTED",
                "Tutor request rejected successfully",
                tutorStudentRequestService.reject(
                        currentUserProvider.getCurrentUserId(), studyingRequestId, tutorRequestId),
                "/api/v1/students/me/studying-requests/" + studyingRequestId
                        + "/tutor-requests/" + tutorRequestId + "/reject"));
    }
}
