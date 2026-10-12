package com.ptutor.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

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

import com.ptutor.backend.entity.enums.RecommendationProfileRole;

/** Long-term preference profile built from behaviour (H1). */
@Entity
@Table(name = "recommendation_user_profiles")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationUserProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    @NonFinal
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationProfileRole role;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "preference_embedding", columnDefinition = "vector(1024)")
    @NonFinal
    private float[] preferenceEmbedding;

    /** Tag code to affinity, for example {"luyen_tap_nhieu": 0.8}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tag_affinity", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode tagAffinity;

    @Column(name = "signal_count", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer signalCount = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @NonFinal
    private LocalDateTime updatedAt;
}
