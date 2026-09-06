package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.LessonService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/me/lessons")
@RequiredArgsConstructor
public class StudentLessonController {

    private static final String BASE_PATH = "/api/v1/students/me/lessons";

    private final LessonService lessonService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/{lessonId}/confirm")
    public ResponseEntity<ApiResponse<LessonResponse>> confirm(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(responseFactory.success(
                lessonService.confirmByStudent(currentUserProvider.getCurrentUserId(), lessonId),
                BASE_PATH + "/" + lessonId + "/confirm"));
    }
}
