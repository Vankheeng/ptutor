package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "wallet_transactions")
@SQLDelete(sql = "UPDATE wallet_transactions SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class WalletTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    @NonFinal
    private Wallet wallet;

    @Column(name = "transaction_type", nullable = false, length = 50)
    @NonFinal
    private String transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    @NonFinal
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    @NonFinal
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", length = 50)
    @NonFinal
    private String referenceType;

    @Column(name = "reference_id")
    @NonFinal
    private UUID referenceId;

    @Column(name = "description", length = 500)
    @NonFinal
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    private String status;
}
