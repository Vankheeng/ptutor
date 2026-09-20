package com.ptutor.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.VnPayPaymentRequest;
import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.response.ContractPaymentInstallmentResponse;
import com.ptutor.backend.dto.response.PaymentInitiationResponse;
import com.ptutor.backend.dto.response.PaymentResponse;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.security.VnPayService;
import com.ptutor.backend.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;
    private final VnPayService vnPayService;

    @PostMapping("/students/me/studying-requests/{requestId}/payments/vnpay")
    public ResponseEntity<ApiResponse<PaymentInitiationResponse>> payStudyingRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) VnPayPaymentRequest request,
            HttpServletRequest servletRequest) {
        String path = "/api/v1/students/me/studying-requests/" + requestId + "/payments/vnpay";
        return ResponseEntity.status(201).body(responseFactory.success(
                paymentService.createStudyingRequestPayment(currentUserProvider.getCurrentUserId(), requestId,
                        request, clientIp(servletRequest)), path));
    }

    @PostMapping("/tutors/me/teaching-requests/{requestId}/payments/vnpay")
    public ResponseEntity<ApiResponse<PaymentInitiationResponse>> payTeachingRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) VnPayPaymentRequest request,
            HttpServletRequest servletRequest) {
        String path = "/api/v1/tutors/me/teaching-requests/" + requestId + "/payments/vnpay";
        return ResponseEntity.status(201).body(responseFactory.success(
                paymentService.createTeachingRequestPayment(currentUserProvider.getCurrentUserId(), requestId,
                        request, clientIp(servletRequest)), path));
    }

    @GetMapping("/students/me/contracts/{contractId}/payment-installments")
    public ResponseEntity<ApiResponse<List<ContractPaymentInstallmentResponse>>> installments(
            @PathVariable UUID contractId) {
        String path = "/api/v1/students/me/contracts/" + contractId + "/payment-installments";
        return ResponseEntity.ok(responseFactory.success(
                paymentService.findMyInstallments(currentUserProvider.getCurrentUserId(), contractId), path));
    }

    @PostMapping("/students/me/contracts/{contractId}/payment-installments/{installmentId}/payments/vnpay")
    public ResponseEntity<ApiResponse<PaymentInitiationResponse>> payTuition(
            @PathVariable UUID contractId,
            @PathVariable UUID installmentId,
            @Valid @RequestBody(required = false) VnPayPaymentRequest request,
            HttpServletRequest servletRequest) {
        String path = "/api/v1/students/me/contracts/" + contractId + "/payment-installments/"
                + installmentId + "/payments/vnpay";
        return ResponseEntity.status(201).body(responseFactory.success(
                paymentService.createTuitionPayment(currentUserProvider.getCurrentUserId(), contractId, installmentId,
                        request, clientIp(servletRequest)), path));
    }

    @GetMapping("/users/me/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> findPayment(@PathVariable UUID paymentId) {
        String path = "/api/v1/users/me/payments/" + paymentId;
        return ResponseEntity.ok(responseFactory.success(
                paymentService.findMine(currentUserProvider.getCurrentUserId(), paymentId), path));
    }

    @GetMapping("/users/me/transactions")
    public ResponseEntity<ApiResponse<PageResponse<FinancialTransactionResponse>>> transactions(
            @RequestParam(required = false) FinancialTransactionSource source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responseFactory.success(paymentService.findMyTransactions(
                currentUserProvider.getCurrentUserId(), source, status, type, method,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), "/api/v1/users/me/transactions"));
    }

    @GetMapping("/payments/vnpay/ipn")
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        PaymentService.VnPayIpnResult result = paymentService.processIpn(params);
        return ResponseEntity.ok(Map.of("RspCode", result.responseCode(), "Message", result.message()));
    }

    @GetMapping("/payments/vnpay/return")
    public ResponseEntity<Void> paymentReturn(@RequestParam Map<String, String> params) {
        PaymentResponse payment = paymentService.inspectReturn(params);
        return ResponseEntity.status(302)
                .location(URI.create(vnPayService.frontendResultUrl(payment.id(), payment.status().name())))
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
