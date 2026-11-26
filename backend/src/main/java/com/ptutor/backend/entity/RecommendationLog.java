package com.ptutor.backend.entity;

import java.util.UUID;

import tools.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.ptutor.backend.entity.enums.RecommendationDirection;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Entity
@Table(name = "recommendation_logs")
@SQLDelete(sql = "UPDATE recommendation_logs SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class RecommendationLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "searcher_student_id")
    @NonFinal
    private Student searcherStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "searcher_tutor_id")
    @NonFinal
    private Tutor searcherTutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_studying_request_id")
    @NonFinal
    private StudyingRequest sourceStudyingRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_teaching_request_id")
    @NonFinal
    private TeachingRequest sourceTeachingRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_context", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode queryContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "returned_candidate_ids", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode returnedCandidateIds;

    @Column(name = "direction", length = 20)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RecommendationDirection direction;

    @Column(name = "algorithm_version", length = 30)
    @NonFinal
    private String algorithmVersion;

    @Column(name = "query_text", columnDefinition = "text")
    @NonFinal
    private String queryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_query", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode parsedQuery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relaxation_suggestions", columnDefinition = "jsonb")
    @NonFinal
    private JsonNode relaxationSuggestions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_search_id")
    @NonFinal
    private RecommendationSavedSearch savedSearch;

    @Column(name = "latency_ms")
    @NonFinal
    private Integer latencyMs;
}
