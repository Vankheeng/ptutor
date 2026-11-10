package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagRole;

import lombok.RequiredArgsConstructor;

/**
 * Writes what the LLM inferred (design 04, 4.6). Rows are marked {@code source = 'LLM'} and only
 * those are ever replaced: anything a user entered ({@code 'USER'}) is never touched.
 */
@Repository
@RequiredArgsConstructor
public class EnrichmentResultRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public int softDeleteInferredTags(RecommendationEntityType entityType, UUID entityId, LocalDateTime now) {
        return jdbc.update("""
                UPDATE recommendation_entity_tags
                   SET deleted_at = :now, updated_at = :now
                 WHERE entity_type = :entityType AND entity_id = :entityId
                   AND source = 'LLM' AND deleted_at IS NULL
                """, entity(entityType, entityId).addValue("now", Timestamp.valueOf(now)));
    }

    /** Skipped when the same tag and role is already there, for example chosen by the user. */
    public int insertInferredTag(
            RecommendationEntityType entityType, UUID entityId, UUID tagId, RecommendationTagRole role,
            double confidence, LocalDateTime now) {
        return jdbc.update("""
                INSERT INTO recommendation_entity_tags
                    (id, entity_type, entity_id, tag_id, role, confidence, source, created_at, updated_at)
                VALUES (gen_random_uuid(), :entityType, :entityId, :tagId, :role, :confidence, 'LLM', :now, :now)
                ON CONFLICT (entity_type, entity_id, tag_id, role) WHERE deleted_at IS NULL DO NOTHING
                """, entity(entityType, entityId)
                        .addValue("tagId", tagId)
                        .addValue("role", role.name())
                        .addValue("confidence", BigDecimal.valueOf(Math.round(confidence * 100) / 100.0))
                        .addValue("now", Timestamp.valueOf(now)));
    }

    public boolean hasUserAvailability(RecommendationEntityType postingType, UUID postingId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table(postingType) + " WHERE " + column(postingType)
                        + " = :id AND source = 'USER' AND deleted_at IS NULL)",
                new MapSqlParameterSource("id", postingId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public int softDeleteInferredAvailabilities(RecommendationEntityType postingType, UUID postingId, LocalDateTime now) {
        return jdbc.update(
                "UPDATE " + table(postingType) + " SET deleted_at = :now, updated_at = :now WHERE "
                        + column(postingType) + " = :id AND source = 'LLM' AND deleted_at IS NULL",
                new MapSqlParameterSource("id", postingId).addValue("now", Timestamp.valueOf(now)));
    }

    public int insertInferredAvailability(
            RecommendationEntityType postingType, UUID postingId, int dayOfWeek, LocalTime start, LocalTime end,
            LocalDateTime now) {
        return jdbc.update(
                "INSERT INTO " + table(postingType) + " (id, " + column(postingType)
                        + ", day_of_week, start_time, end_time, source, created_at, updated_at)"
                        + " VALUES (gen_random_uuid(), :id, :dayOfWeek, :start, :end, 'LLM', :now, :now)",
                new MapSqlParameterSource("id", postingId)
                        .addValue("dayOfWeek", dayOfWeek)
                        .addValue("start", Time.valueOf(start))
                        .addValue("end", Time.valueOf(end))
                        .addValue("now", Timestamp.valueOf(now)));
    }

    private MapSqlParameterSource entity(RecommendationEntityType entityType, UUID entityId) {
        return new MapSqlParameterSource("entityType", entityType.name()).addValue("entityId", entityId);
    }

    /** Table and column names come from this fixed mapping, never from input. */
    private String table(RecommendationEntityType postingType) {
        return switch (postingType) {
            case STUDYING_REQUEST -> "studying_request_availabilities";
            case TEACHING_REQUEST -> "teaching_request_availabilities";
            default -> throw new IllegalArgumentException("Not a posting: " + postingType);
        };
    }

    private String column(RecommendationEntityType postingType) {
        return postingType == RecommendationEntityType.STUDYING_REQUEST ? "studying_request_id" : "teaching_request_id";
    }
}
