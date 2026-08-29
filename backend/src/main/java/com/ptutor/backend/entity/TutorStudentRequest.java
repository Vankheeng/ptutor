package com.ptutor.backend.entity;

import java.math.BigDecimal;

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

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

@Entity
@Table(name = "tutor_student_requests")
@SQLDelete(sql = "UPDATE tutor_student_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class TutorStudentRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    @NonFinal
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    @NonFinal
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "studying_request_id", nullable = false)
    @NonFinal
    private StudyingRequest studyingRequest;

    @Column(name = "proposed_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal proposedPrice;

    @Column(name = "teaching_mode", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private TeachingMode teachingMode;

    @Column(name = "preferred_schedule", length = 500)
    @NonFinal
    private String preferredSchedule;

    @Column(name = "message", columnDefinition = "text")
    @NonFinal
    private String message;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;
}
