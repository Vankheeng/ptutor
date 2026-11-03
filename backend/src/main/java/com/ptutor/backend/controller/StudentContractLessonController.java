package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.LessonService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/contracts/{contractId}/lessons")
@RequiredArgsConstructor
public class StudentContractLessonController {

    private final LessonService lessonService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LessonResponse>>> findAll(@PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                lessonService.findByContractForStudent(currentUserProvider.getCurrentUserId(), contractId),
                "/api/v1/students/me/contracts/" + contractId + "/lessons"));
    }
}
