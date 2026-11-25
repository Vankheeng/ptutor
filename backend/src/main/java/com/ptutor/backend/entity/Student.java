package com.ptutor.backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Entity
@Table(name = "students")
@SQLDelete(sql = "UPDATE students SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Student extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NonFinal
    private User user;

    @Column(name = "introduction", columnDefinition = "text")
    @NonFinal
    private String introduction;

    @Column(name = "learning_style", length = 50)
    @NonFinal
    private String learningStyle;

    @Column(name = "personality_tags", length = 255)
    @NonFinal
    private String personalityTags;

    @Column(name = "goals_description", columnDefinition = "text")
    @NonFinal
    private String goalsDescription;

    @Column(name = "current_level", length = 50)
    @NonFinal
    private String currentLevel;

    @Column(name = "weak_points", columnDefinition = "text")
    @NonFinal
    private String weakPoints;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "profile_embedding", columnDefinition = "vector(1536)")
    @NonFinal
    private float[] profileEmbedding;

    @Column(name = "embedding_source_text", columnDefinition = "text")
    @NonFinal
    private String embeddingSourceText;

    @Column(name = "embedding_updated_at")
    @NonFinal
    private LocalDateTime embeddingUpdatedAt;

    @Column(name = "embedding_model_version", length = 50)
    @NonFinal
    private String embeddingModelVersion;

}
