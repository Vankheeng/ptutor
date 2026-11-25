package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.enums.ComplaintRelation;
import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.response.AdminStudentComplaintResponse;
import com.ptutor.backend.dto.response.AdminStudentLearningHistoryResponse;
import com.ptutor.backend.dto.response.AdminStudentLessonResponse;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminStudentService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
@Validated
public class AdminStudentController {

    private static final String BASE_PATH = "/api/v1/admin/users/";

    private final AdminStudentService studentService;
    private final ApiResponseFactory responseFactory;

    @GetMapping("/student-profile")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> findProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(responseFactory.success(
                studentService.findProfile(userId), path(userId, "/student-profile")));
    }

    @GetMapping("/learning-history")
    public ResponseEntity<ApiResponse<AdminStudentLearningHistoryResponse>> findLearningHistory(
            @PathVariable UUID userId,
            @RequestParam(required = false) LessonStatus lessonStatus,
            @RequestParam(required = false) ContractStatus contractStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("date"), Sort.Order.desc("startTime"), Sort.Order.desc("createdAt")));
        return ResponseEntity.ok(responseFactory.success(
                studentService.findLearningHistory(userId, lessonStatus, contractStatus, pageable),
                path(userId, "/learning-history")));
    }

    @GetMapping("/learning-history/{lessonId}")
    public ResponseEntity<ApiResponse<AdminStudentLessonResponse>> findLesson(
            @PathVariable UUID userId, @PathVariable UUID lessonId) {
        return ResponseEntity.ok(responseFactory.success(
                studentService.findLesson(userId, lessonId),
                path(userId, "/learning-history/" + lessonId)));
    }

    @GetMapping("/contracts")
    public ResponseEntity<ApiResponse<PageResponse<ContractResponse>>> findContracts(
            @PathVariable UUID userId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                studentService.findContracts(userId, status, pageable), path(userId, "/contracts")));
    }

    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<ApiResponse<ContractResponse>> findContract(
            @PathVariable UUID userId, @PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                studentService.findContract(userId, contractId),
                path(userId, "/contracts/" + contractId)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<FinancialTransactionResponse>>> findTransactions(
            @PathVariable UUID userId,
            @RequestParam(required = false) FinancialTransactionSource source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ResponseEntity.ok(responseFactory.success(
                studentService.findTransactions(userId, source, status, type, method, pageable),
                path(userId, "/transactions")));
    }

    @GetMapping("/transactions/{source}/{transactionId}")
    public ResponseEntity<ApiResponse<FinancialTransactionResponse>> findTransaction(
            @PathVariable UUID userId,
            @PathVariable FinancialTransactionSource source,
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(responseFactory.success(
                studentService.findTransaction(userId, source, transactionId),
                path(userId, "/transactions/" + source + "/" + transactionId)));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<PageResponse<AdminStudentComplaintResponse>>> findComplaints(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "ALL") ComplaintRelation relation,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                studentService.findComplaints(userId, relation, status, pageable),
                path(userId, "/complaints")));
    }

    private String path(UUID userId, String suffix) {
        return BASE_PATH + userId + suffix;
    }
}
