package com.ptutor.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.WeightLearningMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** The mode an administrator set for a learned weight model, over the configured one (design 12, 12.9). */
@Entity
@Table(name = "recommendation_learning_modes")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationLearningModeOverride {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "model_key", length = 20)
    @NonFinal
    private WeightModel modelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    @NonFinal
    private WeightLearningMode mode;

    @Column(name = "updated_by")
    @NonFinal
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    @NonFinal
    private LocalDateTime updatedAt;
}
