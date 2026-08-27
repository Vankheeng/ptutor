package com.ptutor.backend.entity;

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
@Table(name = "recommendation_click_logs")
@SQLDelete(sql = "UPDATE recommendation_click_logs SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationClickLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_log_id", nullable = false)
    @NonFinal
    private RecommendationLog recommendationLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_student_id")
    @NonFinal
    private Student clickedStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_tutor_id")
    @NonFinal
    private Tutor clickedTutor;

    @Column(name = "rank", nullable = false)
    @NonFinal
    private Integer rank;

    @Column(name = "action", nullable = false, length = 20)
    @NonFinal
    private String action;
}
