package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.ptutor.backend.entity.enums.TutorProfileStatus;

@Entity
@Table(name = "tutors")
@SQLDelete(sql = "UPDATE tutors SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Tutor extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NonFinal
    private User user;

    @Column(name = "introduction", columnDefinition = "text")
    @NonFinal
    private String introduction;

    @Column(name = "experience_years")
    @NonFinal
    private Integer experienceYears;

    @Column(name = "education", columnDefinition = "text")
    @NonFinal
    private String education;

    @Column(name = "teaching_style_tags", length = 255)
    @NonFinal
    private String teachingStyleTags;

    @Column(name = "teaching_methodology", columnDefinition = "text")
    @NonFinal
    private String teachingMethodology;

    @Column(name = "strength_subjects", columnDefinition = "text")
    @NonFinal
    private String strengthSubjects;

    @Column(name = "target_student_type", length = 255)
    @NonFinal
    private String targetStudentType;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "profile_embedding", columnDefinition = "vector(1536)")
    @NonFinal
    private float[] profileEmbedding;

    @Column(name = "embedding_source_text", columnDefinition = "text")
    @NonFinal
    private String embeddingSourceText;

    @Column(name = "embedding_updated_at")
    @NonFinal
    private LocalDateTime embeddingUpdatedAt;

    @Column(name = "embedding_model_version", length = 50)
    @NonFinal
    private String embeddingModelVersion;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @NonFinal
    private BigDecimal averageRating;

    @Column(name = "total_reviews", nullable = false)
    @NonFinal
    private Integer totalReviews;

    @Column(name = "completed_contracts_count", nullable = false)
    @NonFinal
    private Integer completedContractsCount;

    @Column(name = "total_students_taught", nullable = false)
    @NonFinal
    private Integer totalStudentsTaught;

    @Column(name = "acceptance_rate", precision = 5, scale = 2)
    @NonFinal
    private BigDecimal acceptanceRate;

    @Column(name = "avg_response_time_hours", precision = 6, scale = 2)
    @NonFinal
    private BigDecimal avgResponseTimeHours;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 30)
    @NonFinal
    private TutorProfileStatus profileStatus = TutorProfileStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_reviewed_by")
    @NonFinal
    private Employee profileReviewedBy;

    @Column(name = "profile_reviewed_at")
    @NonFinal
    private LocalDateTime profileReviewedAt;

    @Column(name = "profile_rejection_reason", length = 500)
    @NonFinal
    private String profileRejectionReason;

    @Builder.Default
    @Column(name = "recommendation_score", nullable = false, precision = 5, scale = 2)
    @NonFinal
    private BigDecimal recommendationScore = new BigDecimal("50.00");

    @Column(name = "score_updated_at")
    @NonFinal
    private LocalDateTime scoreUpdatedAt;

    @Builder.Default
    @Column(name = "score_formula_version", nullable = false, length = 30)
    @NonFinal
    private String scoreFormulaVersion = "v1";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode scoreBreakdown;

}
