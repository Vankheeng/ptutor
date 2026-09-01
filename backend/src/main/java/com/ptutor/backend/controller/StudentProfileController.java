package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.UpdateStudentProfileRequest;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.StudentProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
@Validated
public class StudentProfileController {

    private static final String PROFILE_PATH = "/api/v1/students/me";

    private final StudentProfileService studentProfileService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getCurrentProfile() {
        return ResponseEntity.ok(responseFactory.success(
                studentProfileService.getCurrentProfile(), PROFILE_PATH));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateCurrentProfile(
            @Valid @RequestBody UpdateStudentProfileRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                studentProfileService.updateCurrentProfile(request), PROFILE_PATH));
    }
}
