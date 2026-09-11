package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
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
@Table(name = "withdrawal_requests")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class WithdrawalRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    @NonFinal
    private Wallet wallet;

    @Column(nullable = false, precision = 15, scale = 2)
    @NonFinal
    private BigDecimal amount;

    // Snapshot of the destination account at the time the withdrawal is requested.
    @Column(name = "bank_code", nullable = false, length = 20)
    @NonFinal
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    @NonFinal
    private String bankName;

    @Column(name = "encrypted_account_number", nullable = false, length = 512)
    @NonFinal
    private String encryptedAccountNumber;

    @Column(name = "account_number_last_four", nullable = false, length = 4)
    @NonFinal
    private String accountNumberLastFour;

    @Column(name = "account_holder_name", nullable = false, length = 150)
    @NonFinal
    private String accountHolderName;

    @Column(length = 500)
    @NonFinal
    private String note;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    @NonFinal
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @NonFinal
    private WithdrawalRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    @NonFinal
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    @NonFinal
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    @NonFinal
    private String rejectionReason;

    @Column(name = "completed_at")
    @NonFinal
    private LocalDateTime completedAt;

    @Column(name = "transfer_reference", length = 100)
    @NonFinal
    private String transferReference;
}
