package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/** Cross-encoder score cache shared by search and background jobs. */
@Entity
@Table(name = "recommendation_rerank_cache")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationRerankCache {

    @EmbeddedId
    @NonFinal
    private RecommendationRerankCacheId id;

    @Column(name = "candidate_content_hash", nullable = false, length = 64)
    @NonFinal
    private String candidateContentHash;

    /** In [0, 1]. */
    @Column(name = "rerank_score", nullable = false, precision = 5, scale = 4)
    @NonFinal
    private BigDecimal rerankScore;

    @Column(name = "computed_at", nullable = false)
    @NonFinal
    private LocalDateTime computedAt;

    @PrePersist
    void stampComputedAt() {
        if (computedAt == null) {
            computedAt = LocalDateTime.now();
        }
    }
}
