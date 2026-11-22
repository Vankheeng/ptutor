package com.ptutor.backend.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.enums.AdminTransactionFlow;
import com.ptutor.backend.dto.response.AdminTransactionResponse;
import com.ptutor.backend.dto.response.AdminTransactionSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AdminTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {
    private final AdminTransactionRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> findAll(
            FinancialTransactionSource source, AdminTransactionFlow flow, String status, String type, String method, String search,
            LocalDate fromDate, LocalDate toDate, int page, int size) {
        validateDates(fromDate, toDate);
        Page<AdminTransactionResponse> result = repository.findAll(source, flow, status, type, method, search,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        return PageResponse.from(result, result.getContent());
    }

    @Transactional(readOnly = true)
    public AdminTransactionSummaryResponse summarize(
            FinancialTransactionSource source, AdminTransactionFlow flow, String status, String type, String method,
            String search, LocalDate fromDate, LocalDate toDate) {
        validateDates(fromDate, toDate);
        return repository.summarize(source, flow, status, type, method, search,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public AdminTransactionResponse findById(FinancialTransactionSource source, UUID id) {
        return repository.findById(source, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + id));
    }

    private void validateDates(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "fromDate must not be after toDate");
        }
    }
}
