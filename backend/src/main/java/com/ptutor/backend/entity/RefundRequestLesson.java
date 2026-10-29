package com.ptutor.backend.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ptutor.backend.entity.enums.LessonOutcome;

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

/**
 * A lesson the student disputed: the student's claim, the tutor's version and the reviewer's decision.
 */
@Entity
@Table(name = "refund_request_lessons")
@SQLDelete(sql = "UPDATE refund_request_lessons SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RefundRequestLesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_request_id", nullable = false)
    @NonFinal
    private RefundRequest refundRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    @NonFinal
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_outcome", nullable = false, length = 20)
    @NonFinal
    private LessonOutcome claimedOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_position", length = 20)
    @NonFinal
    private LessonOutcome tutorPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    @NonFinal
    private LessonOutcome decision;
}
