package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;

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
@Table(name = "contract_payment_installments")
@SQLDelete(sql = "UPDATE contract_payment_installments SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class ContractPaymentInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    @NonFinal
    private Contract contract;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    @NonFinal
    private Lesson lesson;

    @Column(name = "sequence_number", nullable = false)
    @NonFinal
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_period", nullable = false, length = 30)
    @NonFinal
    private PaymentPeriod paymentPeriod;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    @NonFinal
    private BigDecimal amount;

    @Column(name = "due_date")
    @NonFinal
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    private PaymentInstallmentStatus status;
}
