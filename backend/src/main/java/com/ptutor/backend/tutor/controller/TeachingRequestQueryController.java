package com.ptutor.backend.tutor.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.tutor.dto.TeachingRequestResponse;
import com.ptutor.backend.tutor.service.TeachingRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/teaching-requests")
@RequiredArgsConstructor
public class TeachingRequestQueryController {

    private final TeachingRequestService teachingRequestService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeachingRequestResponse>>> findVisible() {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.findVisible(currentUserProvider.getCurrentUserRole()),
                "/api/v1/teaching-requests"));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<TeachingRequestResponse>> findVisibleById(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.findVisibleById(requestId, currentUserProvider.getCurrentUserRole()),
                "/api/v1/teaching-requests/" + requestId));
    }
}
