package com.ptutor.backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/** Impression and click counters per candidate posting (R6). */
@Entity
@Table(name = "recommendation_exposure_stats")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationExposureStats {

    @EmbeddedId
    @NonFinal
    private RecommendationExposureStatsId id;

    @Column(name = "impressions_7d", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer impressions7d = 0;

    @Column(name = "clicks_7d", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer clicks7d = 0;

    @Column(name = "impressions_total", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer impressionsTotal = 0;

    @Column(name = "clicks_total", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer clicksTotal = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @NonFinal
    private LocalDateTime updatedAt;
}
