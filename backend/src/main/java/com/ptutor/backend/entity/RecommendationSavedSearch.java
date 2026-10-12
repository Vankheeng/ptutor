package com.ptutor.backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

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

import com.ptutor.backend.entity.enums.RecommendationDirection;

@Entity
@Table(name = "recommendation_saved_searches")
@SQLDelete(sql = "UPDATE recommendation_saved_searches SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationSavedSearch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NonFinal
    private User user;

    @Column(name = "direction", nullable = false, length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationDirection direction;

    @Column(name = "name", length = 120)
    @NonFinal
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_context", nullable = false, columnDefinition = "jsonb")
    @NonFinal
    private JsonNode queryContext;

    @Column(name = "query_text_hash", length = 64)
    @NonFinal
    private String queryTextHash;

    @Column(name = "notify_enabled", nullable = false)
    @NonFinal
    @Builder.Default
    private Boolean notifyEnabled = true;

    @Column(name = "last_notified_at")
    @NonFinal
    private LocalDateTime lastNotifiedAt;
}
