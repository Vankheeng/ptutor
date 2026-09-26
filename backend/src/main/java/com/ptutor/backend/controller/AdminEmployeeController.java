package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.CreateEmployeeRequest;
import com.ptutor.backend.dto.request.EmployeeAccessRequest;
import com.ptutor.backend.dto.request.UpdateEmployeeRequest;
import com.ptutor.backend.dto.response.AdminEmployeeDetailResponse;
import com.ptutor.backend.dto.response.AdminEmployeeSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminEmployeeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/employees")
@RequiredArgsConstructor
@Validated
public class AdminEmployeeController {

    private static final String BASE_PATH = "/api/v1/admin/employees";

    private final AdminEmployeeService employeeService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminEmployeeSummaryResponse>>> findAll(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) EmployeeJobFunction jobFunction,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                employeeService.findAll(status, jobFunction, keyword, pageable), BASE_PATH));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminEmployeeDetailResponse>> create(
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success(
                "EMPLOYEE_CREATED", "Employee account created successfully",
                employeeService.create(currentUserProvider.getCurrentUserId(), request), BASE_PATH));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<AdminEmployeeDetailResponse>> findById(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(responseFactory.success(
                employeeService.findById(employeeId), BASE_PATH + "/" + employeeId));
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<AdminEmployeeDetailResponse>> update(
            @PathVariable UUID employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "EMPLOYEE_UPDATED", "Employee information updated successfully",
                employeeService.update(currentUserProvider.getCurrentUserId(), employeeId, request),
                BASE_PATH + "/" + employeeId));
    }

    @PatchMapping("/{employeeId}/deactivate")
    public ResponseEntity<ApiResponse<AdminEmployeeDetailResponse>> deactivate(
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeAccessRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "EMPLOYEE_DEACTIVATED", "Employee access revoked successfully",
                employeeService.deactivate(currentUserProvider.getCurrentUserId(), employeeId, request.reason()),
                BASE_PATH + "/" + employeeId + "/deactivate"));
    }

    @PatchMapping("/{employeeId}/reactivate")
    public ResponseEntity<ApiResponse<AdminEmployeeDetailResponse>> reactivate(
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeAccessRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "EMPLOYEE_REACTIVATED", "Employee access restored successfully",
                employeeService.reactivate(currentUserProvider.getCurrentUserId(), employeeId, request.reason()),
                BASE_PATH + "/" + employeeId + "/reactivate"));
    }
}
