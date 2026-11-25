package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import com.ptutor.backend.entity.Payment;

class VnPayServiceTest {

    @Test
    void paymentUrlUsesConfiguredTimeZoneForVnPayTimestamps() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T15:29:58Z"), ZoneOffset.UTC);
        VnPayService service = new VnPayService(
                "TESTCODE",
                "test-secret",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:8080/api/v1/payments/vnpay/return",
                "http://localhost:5173/payment-result",
                15,
                "Asia/Ho_Chi_Minh",
                clock);
        Payment payment = Payment.builder()
                .amount(new BigDecimal("10000"))
                .transactionCode("PT-TEST-001")
                .expiresAt(LocalDateTime.parse("2026-09-20T15:44:58"))
                .build();

        String url = service.createPaymentUrl(payment, "127.0.0.1", null);
        var query = UriComponentsBuilder.fromUriString(url).build(true).getQueryParams();

        assertThat(query.getFirst("vnp_CreateDate")).isEqualTo("20260920222958");
        assertThat(query.getFirst("vnp_ExpireDate")).isEqualTo("20260920224458");
    }

    @Test
    void paymentExpiryRemainsStoredAsUtcLocalDateTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T15:29:58Z"), ZoneOffset.UTC);
        VnPayService service = new VnPayService(
                "TESTCODE", "test-secret", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:8080/api/v1/payments/vnpay/return", "http://localhost:5173/payment-result",
                15, "Asia/Ho_Chi_Minh", clock);

        assertThat(service.expiresAt()).isEqualTo(LocalDateTime.parse("2026-09-20T15:44:58"));
    }
}
