package com.ptutor.backend.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;

/** Tag vocabulary shared by enrichment (A1) and the search filter chips. */
@Entity
@Table(name = "recommendation_tags")
@SQLDelete(sql = "UPDATE recommendation_tags SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationTag extends BaseEntity {

    @Column(name = "tag_group", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationTagGroup tagGroup;

    /** Only set for TOPIC tags. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @NonFinal
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @NonFinal
    private RecommendationTag parent;

    @Column(name = "code", nullable = false, length = 60)
    @NonFinal
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    @NonFinal
    private String name;

    @Column(name = "description", length = 500)
    @NonFinal
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecommendationTagStatus status = RecommendationTagStatus.ACTIVE;
}
