package com.ptutor.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.SubjectResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.SubjectService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final SubjectService subjectService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> findAll() {
        return ResponseEntity.ok(responseFactory.success(
                subjectService.findAllSubjects(), "/api/v1/admin/subjects"));
    }
}
