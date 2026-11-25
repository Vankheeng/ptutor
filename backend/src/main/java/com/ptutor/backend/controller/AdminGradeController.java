package com.ptutor.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.GradeResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.GradeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/grades")
@RequiredArgsConstructor
public class AdminGradeController {

    private final GradeService gradeService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GradeResponse>>> findAll() {
        return ResponseEntity.ok(responseFactory.success(
                gradeService.findAllGrades(), "/api/v1/admin/grades"));
    }
}
