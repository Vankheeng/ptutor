package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

@Entity
@Table(name = "contracts")
@SQLDelete(sql = "UPDATE contracts SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Contract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    @NonFinal
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id")
    @NonFinal
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @NonFinal
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @NonFinal
    private Grade grade;

    @Column(name = "teaching_mode", columnDefinition = "varchar")
    @NonFinal
    @Enumerated(EnumType.STRING)
    private TeachingMode teachingMode;

    @Column(name = "price", columnDefinition = "decimal")
    @NonFinal
    private BigDecimal price;

    @Column(name = "payment_period", columnDefinition = "varchar")
    @NonFinal
    private String paymentPeriod;

    @Column(name = "total_lession")
    @NonFinal
    private Integer totalLession;

    @Column(name = "start_date")
    @NonFinal
    private LocalDate startDate;

    @Column(name = "end_date")
    @NonFinal
    private LocalDate endDate;

    @Column(name = "status", columnDefinition = "varchar")
    @NonFinal
    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    @Column(name = "signed_at")
    @NonFinal
    private LocalDateTime signedAt;
}
