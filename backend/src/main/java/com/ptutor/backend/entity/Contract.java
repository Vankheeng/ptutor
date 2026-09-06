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
    @JoinColumn(name = "student_id", nullable = false)
    @NonFinal
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    @NonFinal
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    @NonFinal
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    @NonFinal
    private Grade grade;

    @Column(name = "teaching_mode", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private TeachingMode teachingMode;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    @NonFinal
    private BigDecimal price;

    @Column(name = "payment_period", nullable = false, length = 100)
    @NonFinal
    private String paymentPeriod;

    @Column(name = "total_lession", nullable = false)
    @NonFinal
    private Integer totalLession;

    @Column(name = "start_date", nullable = false)
    @NonFinal
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @NonFinal
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    @Column(name = "signed_at")
    @NonFinal
    private LocalDateTime signedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    @NonFinal
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_by_user_id")
    @NonFinal
    private User signedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_student_request_id")
    @NonFinal
    private TutorStudentRequest tutorStudentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_tutor_request_id")
    @NonFinal
    private StudentTutorRequest studentTutorRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewed_from_contract_id")
    @NonFinal
    private Contract renewedFromContract;

    @Column(name = "preferred_schedule", nullable = false, length = 500)
    @NonFinal
    private String preferredSchedule;
}
