package com.ptutor.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.DecisionRequestType;
import com.ptutor.backend.entity.enums.DecisionSampleKind;

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
 * The willingness features of a pair when an application or proposal was created (design 12, 12.6 and 12.8).
 * Written by {@code DecisionSnapshotRecorder} with plain SQL; this entity is for reading.
 */
@Entity
@Table(name = "recommendation_decision_snapshots")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationDecisionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonFinal
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_key", nullable = false, length = 20)
    @NonFinal
    private WeightModel modelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_kind", nullable = false, length = 20)
    @NonFinal
    private DecisionSampleKind sampleKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    @NonFinal
    private DecisionRequestType requestType;

    @Column(name = "request_id", nullable = false)
    @NonFinal
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode features;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weights_version", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode weightsVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @NonFinal
    private LocalDateTime createdAt;
}
