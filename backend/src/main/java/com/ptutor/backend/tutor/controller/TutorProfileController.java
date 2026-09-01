package com.ptutor.backend.tutor.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.tutor.dto.TutorProfileResponse;
import com.ptutor.backend.tutor.dto.TutorSelfProfileResponse;
import com.ptutor.backend.tutor.service.TutorProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors")
@RequiredArgsConstructor
public class TutorProfileController {

    private final TutorProfileService tutorProfileService;
    private final ApiResponseFactory responseFactory;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<TutorSelfProfileResponse>> findMine(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(responseFactory.success(
                tutorProfileService.findMine(userId(jwt)), "/api/v1/tutors/me"));
    }

    @GetMapping("/{tutorId}")
    public ResponseEntity<ApiResponse<TutorProfileResponse>> findById(@PathVariable UUID tutorId) {
        return ResponseEntity.ok(responseFactory.success(
                tutorProfileService.findById(tutorId), "/api/v1/tutors/" + tutorId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
