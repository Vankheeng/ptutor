package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationMatchSourceType;

/** A match alert already sent, so the same pair is never notified twice. */
@Entity
@Table(name = "recommendation_match_alerts")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationMatchAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @NonFinal
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    @NonFinal
    private User recipientUser;

    @Column(name = "source_type", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationMatchSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    @NonFinal
    private UUID sourceId;

    @Column(name = "candidate_type", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationCandidateType candidateType;

    @Column(name = "candidate_id", nullable = false)
    @NonFinal
    private UUID candidateId;

    @Column(name = "final_score", nullable = false, precision = 5, scale = 4)
    @NonFinal
    private BigDecimal finalScore;

    @Column(name = "candidate_rank", nullable = false)
    @NonFinal
    private Integer candidateRank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    @NonFinal
    private Notification notification;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @NonFinal
    private LocalDateTime createdAt;
}
