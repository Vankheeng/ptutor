package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PublicStudentProfileResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.StudentProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class PublicStudentProfileController {

    private final StudentProfileService studentProfileService;
    private final ApiResponseFactory responseFactory;

    @GetMapping("/{studentId}/profile")
    public ResponseEntity<ApiResponse<PublicStudentProfileResponse>> getProfile(@PathVariable UUID studentId) {
        return ResponseEntity.ok(responseFactory.success(
                studentProfileService.getPublicProfile(studentId), "/api/v1/students/" + studentId + "/profile"));
    }
}
