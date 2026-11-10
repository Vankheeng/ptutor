package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ptutor.backend.entity.enums.RefundRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@Table(name = "refund_requests")
@SQLDelete(sql = "UPDATE refund_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RefundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    @NonFinal
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @NonFinal
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @NonFinal
    private Employee employee;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    @NonFinal
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NonFinal
    private RefundRequestStatus status;

    @Column(name = "tutor_response", columnDefinition = "text")
    @NonFinal
    private String tutorResponse;

    @Column(name = "tutor_responded_at")
    @NonFinal
    private LocalDateTime tutorRespondedAt;

    @Column(name = "tutor_response_deadline", nullable = false)
    @NonFinal
    private LocalDateTime tutorResponseDeadline;

    // Snapshot of the calculation, written when the request is approved.
    @Column(name = "unit_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal unitPrice;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal refundAmount;

    @Column(name = "tutor_share_amount", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal tutorShareAmount;

    @Column(name = "note", columnDefinition = "text")
    @NonFinal
    private String note;

    @Column(name = "rejection_reason", length = 500)
    @NonFinal
    private String rejectionReason;

    @Column(name = "reviewed_at")
    @NonFinal
    private LocalDateTime reviewedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_transaction_id", unique = true)
    @NonFinal
    private WalletTransaction walletTransaction;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_wallet_transaction_id", unique = true)
    @NonFinal
    private WalletTransaction tutorWalletTransaction;
}
