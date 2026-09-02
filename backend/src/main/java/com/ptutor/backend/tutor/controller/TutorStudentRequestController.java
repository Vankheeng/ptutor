package com.ptutor.backend.tutor.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.tutor.dto.TutorStudentRequestRequest;
import com.ptutor.backend.tutor.dto.TutorStudentRequestResponse;
import com.ptutor.backend.tutor.service.TutorStudentRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/tutor-student-requests")
@RequiredArgsConstructor
@Validated
public class TutorStudentRequestController {

    private static final String PROPOSALS_PATH = "/api/v1/tutors/me/tutor-student-requests";

    private final TutorStudentRequestService tutorStudentRequestService;
    private final ApiResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TutorStudentRequestRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                tutorStudentRequestService.create(userId(jwt), request), PROPOSALS_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TutorStudentRequestResponse>>> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                tutorStudentRequestService.findMine(userId(jwt), status), PROPOSALS_PATH));
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> findMineById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID proposalId) {
        return ResponseEntity.ok(responseFactory.success(
                tutorStudentRequestService.findMineById(userId(jwt), proposalId),
                PROPOSALS_PATH + "/" + proposalId));
    }

    @PostMapping("/{proposalId}/cancel")
    public ResponseEntity<ApiResponse<TutorStudentRequestResponse>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID proposalId) {
        return ResponseEntity.ok(responseFactory.success(
                "TUTOR_PROPOSAL_CANCELLED",
                "Tutor proposal cancelled successfully",
                tutorStudentRequestService.cancel(userId(jwt), proposalId),
                PROPOSALS_PATH + "/" + proposalId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
