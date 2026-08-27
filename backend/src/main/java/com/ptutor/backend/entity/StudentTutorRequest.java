package com.ptutor.backend.entity;

import java.math.BigDecimal;

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
@Table(name = "student_tutor_requests")
@SQLDelete(sql = "UPDATE student_tutor_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class StudentTutorRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @NonFinal
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    @NonFinal
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teaching_request_id", nullable = false)
    @NonFinal
    private TeachingRequest teachingRequest;

    @Column(name = "proposed_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal proposedPrice;

    @Column(name = "learning_mode", nullable = false, length = 30)
    @NonFinal
    private String learningMode;

    @Column(name = "preferred_schedule", length = 500)
    @NonFinal
    private String preferredSchedule;

    @Column(name = "message", columnDefinition = "text")
    @NonFinal
    private String message;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    private String status;
}
