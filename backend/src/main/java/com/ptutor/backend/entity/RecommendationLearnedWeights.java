package com.ptutor.backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ptutor.backend.dto.recommendation.WeightModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import tools.jackson.databind.JsonNode;

/**
 * One version of the learned weights of a model, with the statistics needed to continue learning from it
 * (design 12, section 12.8). Versions are only added, never overwritten; at most one per model is active.
 */
@Entity
@Table(name = "recommendation_learned_weights")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationLearnedWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonFinal
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_key", nullable = false, length = 20)
    @NonFinal
    private WeightModel modelKey;

    @Column(name = "version", nullable = false)
    @NonFinal
    private Integer version;

    /** ["intercept", "sem", ...]: the order of {@code statsA} and {@code statsC}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_names", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode featureNames;

    /** Clamped and normalised: what the ranking uses. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weights", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode weights;

    /** μ before clamping, for analysis. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "posterior_mean", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode posteriorMean;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats_a", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode statsA;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats_c", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode statsC;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prior_weights", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode priorWeights;

    @Column(name = "sample_count", nullable = false)
    @NonFinal
    private Integer sampleCount;

    @Column(name = "trained_until", nullable = false)
    @NonFinal
    private LocalDateTime trainedUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode evaluation;

    @Column(name = "is_active", nullable = false)
    @NonFinal
    @Builder.Default
    private Boolean active = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @NonFinal
    private LocalDateTime createdAt;
}
