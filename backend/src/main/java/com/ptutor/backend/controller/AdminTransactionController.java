package com.ptutor.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.enums.AdminTransactionFlow;
import com.ptutor.backend.dto.response.AdminTransactionResponse;
import com.ptutor.backend.dto.response.AdminTransactionSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminTransactionService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
@Validated
public class AdminTransactionController {
    private static final String BASE = "/api/v1/admin/transactions";
    private final AdminTransactionService service;
    private final ApiResponseFactory responses;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminTransactionResponse>>> findAll(
            @RequestParam(required = false) FinancialTransactionSource source,
            @RequestParam(required = false) AdminTransactionFlow flow,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responses.success(service.findAll(
                source, flow, status, type, method, search, fromDate, toDate, page, size), BASE));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminTransactionSummaryResponse>> summarize(
            @RequestParam(required = false) FinancialTransactionSource source,
            @RequestParam(required = false) AdminTransactionFlow flow,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(responses.success(service.summarize(
                source, flow, status, type, method, search, fromDate, toDate), BASE + "/summary"));
    }

    @GetMapping("/{source}/{id}")
    public ResponseEntity<ApiResponse<AdminTransactionResponse>> findById(
            @PathVariable FinancialTransactionSource source, @PathVariable UUID id) {
        return ResponseEntity.ok(responses.success(service.findById(source, id), BASE + "/" + source + "/" + id));
    }
}
