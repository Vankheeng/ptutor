package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.util.UUID;

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

import com.ptutor.backend.entity.enums.RecommendationDataSource;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagRole;

/** A tag attached to a profile or a posting. */
@Entity
@Table(name = "recommendation_entity_tags")
@SQLDelete(sql = "UPDATE recommendation_entity_tags SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationEntityTag extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationEntityType entityType;

    /** Polymorphic reference, no foreign key: the application keeps it consistent. */
    @Column(name = "entity_id", nullable = false)
    @NonFinal
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    @NonFinal
    private RecommendationTag tag;

    @Column(name = "role", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationTagRole role;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    @NonFinal
    @Builder.Default
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "source", nullable = false, length = 10)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationDataSource source;
}
