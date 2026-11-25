package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.EmployeeSelfResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.EmployeeSelfService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/employees/me")
@RequiredArgsConstructor
public class EmployeeSelfController {

    private static final String BASE_PATH = "/api/v1/employees/me";

    private final EmployeeSelfService employeeSelfService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<EmployeeSelfResponse>> findMine() {
        return ResponseEntity.ok(responseFactory.success(
                employeeSelfService.findMine(currentUserProvider.getCurrentUserId()), BASE_PATH));
    }
}
