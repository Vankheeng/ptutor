package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudyingRequestSearchResponse;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.StudyingRequestService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/studying-requests")
@RequiredArgsConstructor
@Validated
public class StudyingRequestSearchController {

    private final StudyingRequestService studyingRequestService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StudyingRequestSearchResponse>>> search(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID gradeId,
            @RequestParam(required = false) UUID districtId,
            @RequestParam(required = false) LearningMode learningMode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication) {
        boolean tutor = hasRole(authentication, "ROLE_TUTOR");
        var result = studyingRequestService.search(status, subjectId, gradeId, districtId, learningMode,
                tutor, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(responseFactory.success(result, "/api/v1/studying-requests"));
    }

    @GetMapping("/{studyingRequestId}")
    public ResponseEntity<ApiResponse<StudyingRequestSearchResponse>> findById(
            @PathVariable UUID studyingRequestId, Authentication authentication) {
        var result = studyingRequestService.findSearchableById(
                studyingRequestId, hasRole(authentication, "ROLE_TUTOR"));
        return ResponseEntity.ok(responseFactory.success(
                result, "/api/v1/studying-requests/" + studyingRequestId));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
