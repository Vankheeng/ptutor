package com.ptutor.backend.security;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.ptutor.backend.dto.request.VnPayPaymentRequest;
import com.ptutor.backend.entity.Payment;

@Service
public class VnPayService {

    private static final DateTimeFormatter VNP_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final String tmnCode;
    private final String hashSecret;
    private final String paymentUrl;
    private final String returnUrl;
    private final String frontendReturnUrl;
    private final int expiryMinutes;
    private final ZoneId zoneId;
    private final Clock clock;

    public VnPayService(
            @Value("${app.payment.vnpay.tmn-code}") String tmnCode,
            @Value("${app.payment.vnpay.hash-secret}") String hashSecret,
            @Value("${app.payment.vnpay.payment-url}") String paymentUrl,
            @Value("${app.payment.vnpay.return-url}") String returnUrl,
            @Value("${app.payment.vnpay.frontend-return-url}") String frontendReturnUrl,
            @Value("${app.payment.vnpay.expiry-minutes:15}") int expiryMinutes,
            @Value("${app.payment.vnpay.time-zone:Asia/Ho_Chi_Minh}") String timeZone,
            Clock clock) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
        this.frontendReturnUrl = frontendReturnUrl;
        this.expiryMinutes = expiryMinutes;
        this.zoneId = ZoneId.of(timeZone);
        this.clock = clock;
    }

    public LocalDateTime expiresAt() {
        return nowUtc().plusMinutes(expiryMinutes);
    }

    public String createPaymentUrl(Payment payment, String clientIp, VnPayPaymentRequest request) {
        ensureConfigured();
        LocalDateTime createdAt = toVnPayDateTime(nowUtc());
        LocalDateTime expiresAt = toVnPayDateTime(payment.getExpiresAt());
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", payment.getAmount().setScale(0, RoundingMode.UNNECESSARY)
                .multiply(java.math.BigDecimal.valueOf(100)).toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTransactionCode());
        params.put("vnp_OrderInfo", "PTutor payment " + payment.getTransactionCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", request != null && "en".equals(request.locale()) ? "en" : "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", createdAt.format(VNP_DATE_TIME));
        params.put("vnp_ExpireDate", expiresAt.format(VNP_DATE_TIME));
        if (request != null && request.bankCode() != null && !request.bankCode().isBlank()) {
            params.put("vnp_BankCode", request.bankCode().strip());
        }
        String signature = sign(params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(paymentUrl);
        params.forEach(builder::queryParam);
        return builder.queryParam("vnp_SecureHash", signature).build().encode().toUriString();
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Payment expiry timestamps are stored as UTC LocalDateTime values. */
    private LocalDateTime toVnPayDateTime(LocalDateTime utcDateTime) {
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(zoneId)
                .toLocalDateTime();
    }

    public boolean isValidSignature(Map<String, String> source) {
        if (hashSecret == null || hashSecret.isBlank()) {
            return false;
        }
        String suppliedHash = source.get("vnp_SecureHash");
        if (suppliedHash == null || suppliedHash.isBlank()) {
            return false;
        }
        Map<String, String> values = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key) && value != null && !value.isBlank()) {
                values.put(key, value);
            }
        });
        return constantTimeEquals(sign(values), suppliedHash);
    }

    public boolean isExpectedMerchant(String value) {
        return tmnCode.equals(value);
    }

    public String frontendResultUrl(UUID paymentId, String status) {
        return UriComponentsBuilder.fromUriString(frontendReturnUrl)
                .queryParam("paymentId", paymentId)
                .queryParam("status", status)
                .build().encode().toUriString();
    }

    private String sign(Map<String, String> values) {
        String raw = values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign VNPay request", exception);
        }
    }

    private void ensureConfigured() {
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()
                || paymentUrl == null || paymentUrl.isBlank() || returnUrl == null || returnUrl.isBlank()) {
            throw new IllegalStateException("VNPay configuration is incomplete");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII).replace("+", "%20");
    }

    private String normalizeIp(String value) {
        return value == null || value.isBlank() ? "127.0.0.1" : value.strip();
    }

    private boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }
}
