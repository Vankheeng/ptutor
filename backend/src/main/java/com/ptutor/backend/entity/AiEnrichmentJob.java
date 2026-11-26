package com.ptutor.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;

/** Outbox row for background AI work; written in the same transaction as the change it follows. */
@Entity
@Table(name = "ai_enrichment_jobs")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class AiEnrichmentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @NonFinal
    private UUID id;

    @Column(name = "job_type", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private AiJobType jobType;

    @Column(name = "entity_type", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    @NonFinal
    private UUID entityId;

    @Column(name = "content_hash", length = 64)
    @NonFinal
    private String contentHash;

    @Column(name = "status", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AiJobStatus status = AiJobStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @NonFinal
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    @NonFinal
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    @NonFinal
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @NonFinal
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @NonFinal
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    @NonFinal
    private LocalDateTime processedAt;

    @PrePersist
    void scheduleImmediatelyByDefault() {
        if (nextAttemptAt == null) {
            nextAttemptAt = LocalDateTime.now();
        }
    }
}
