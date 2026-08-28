package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@Table(name = "payments")
@SQLDelete(sql = "UPDATE payments SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @NonFinal
    private User user;

    @Column(name = "amount", columnDefinition = "decimal")
    @NonFinal
    private BigDecimal amount;

    @Column(name = "payment_method", columnDefinition = "varchar")
    @NonFinal
    private String paymentMethod;

    @Column(name = "payment_type", columnDefinition = "varchar")
    @NonFinal
    private String paymentType;

    @Column(name = "status", columnDefinition = "varchar")
    @NonFinal
    private String status;

    @Column(name = "transaction_code", columnDefinition = "varchar")
    @NonFinal
    private String transactionCode;

    @Column(name = "reference_type", length = 50)
    @NonFinal
    private String referenceType;

    @Column(name = "reference_id")
    @NonFinal
    private UUID referenceId;

    @Column(name = "note", columnDefinition = "varchar")
    @NonFinal
    private String note;

    @Column(name = "paid_at")
    @NonFinal
    private LocalDateTime paidAt;
}
