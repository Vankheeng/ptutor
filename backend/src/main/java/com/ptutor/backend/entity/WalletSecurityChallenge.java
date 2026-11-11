package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ptutor.backend.entity.enums.WalletSecurityChallengePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Entity
@Table(name = "wallet_security_challenges")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class WalletSecurityChallenge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NonFinal
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @NonFinal
    private WalletSecurityChallengePurpose purpose;

    @Column(name = "otp_hash", nullable = false, length = 255)
    @NonFinal
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    @NonFinal
    private LocalDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    @NonFinal
    private int failedAttempts;

    @Column(name = "consumed_at")
    @NonFinal
    private LocalDateTime consumedAt;

    @Column(name = "invalidated_at")
    @NonFinal
    private LocalDateTime invalidatedAt;

    @Column(precision = 15, scale = 2)
    @NonFinal
    private BigDecimal amount;

    @Column(length = 500)
    @NonFinal
    private String note;

    @Column(name = "bank_code", length = 20)
    @NonFinal
    private String bankCode;

    @Column(name = "bank_name", length = 100)
    @NonFinal
    private String bankName;

    @Column(name = "encrypted_account_number", length = 512)
    @NonFinal
    private String encryptedAccountNumber;

    @Column(name = "account_number_last_four", length = 4)
    @NonFinal
    private String accountNumberLastFour;

    @Column(name = "account_holder_name", length = 150)
    @NonFinal
    private String accountHolderName;

    @Column(name = "idempotency_key", length = 100)
    @NonFinal
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_request_id")
    @NonFinal
    private WithdrawalRequest withdrawalRequest;
}
