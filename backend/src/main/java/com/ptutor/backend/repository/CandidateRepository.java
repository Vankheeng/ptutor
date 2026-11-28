package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.service.RecommendationSettings;

import lombok.RequiredArgsConstructor;

/**
 * Candidate retrieval for "student looks for tutors": the hard filters and the vector distance
 * run in one SQL statement (design 06, 6.2-6.3), then the data needed for scoring is loaded in
 * a fixed number of queries regardless of how many candidates there are.
 */
@Repository
@RequiredArgsConstructor
public class CandidateRepository {

    /** How long a rejection keeps the same posting hidden, and how long it lowers the tutor's other postings. */
    static final int HIDE_REJECTED_POSTING_DAYS = 30;
    static final int RECENT_REJECTION_DAYS = 90;

    private static final String HARD_FILTER = """
            SELECT tr.id AS teaching_request_id,
                   tr.created_at,
                   1 - (tr.embedding <=> CAST(:queryVector AS vector)) AS cosine
            FROM teaching_requests tr
            JOIN tutors t ON t.id = tr.tutor_id AND t.deleted_at IS NULL
            JOIN users  u ON u.id = t.user_id  AND u.deleted_at IS NULL
            WHERE tr.deleted_at IS NULL
              AND tr.status = 'OPEN'
              AND t.profile_status = 'VERIFIED'
              AND u.status = 'ACTIVE'
              AND tr.subject_id = :subjectId
              AND EXISTS (SELECT 1 FROM grade_teaching_requests g
                          WHERE g.teaching_request_id = tr.id AND g.grade_id = :gradeId AND g.deleted_at IS NULL)
              AND (:learningMode = 'ANY' OR tr.teaching_mode = :learningMode)
              AND (tr.teaching_mode = 'ONLINE'
                   OR :areaScope = 'ANY'
                   OR EXISTS (SELECT 1 FROM teaching_request_districts d
                              JOIN districts dd ON dd.id = d.district_id
                              WHERE d.teaching_request_id = tr.id AND d.deleted_at IS NULL
                                AND ((:areaScope = 'DISTRICTS' AND d.district_id = ANY(CAST(:districtIds AS uuid[])))
                                  OR (:areaScope = 'PROVINCE'  AND dd.province_id = CAST(:provinceId AS uuid)))))
              AND (CAST(:maxPrice AS numeric) IS NULL OR tr.expected_price IS NULL
                   OR tr.expected_price <= CAST(:maxPrice AS numeric) * :priceHardFactor)
              AND NOT EXISTS (SELECT 1 FROM contracts c
                              WHERE c.student_id = :studentId AND c.tutor_id = tr.tutor_id
                                AND c.subject_id = tr.subject_id AND c.status IN ('PENDING', 'ACTIVE')
                                AND c.deleted_at IS NULL)
              AND NOT EXISTS (SELECT 1 FROM student_tutor_requests s
                              WHERE s.student_id = :studentId AND s.teaching_request_id = tr.id
                                AND s.deleted_at IS NULL
                                AND (s.status IN ('PENDING', 'ACCEPTED')
                                     OR (s.status = 'REJECTED'
                                         AND COALESCE(s.responded_at, s.updated_at) > :hideRejectedSince)))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RecommendationSettings settings;

    /** Postings that pass every hard filter, each with its cosine similarity to the query (may be null). */
    public List<Hit> findHits(QueryContext context, float[] queryVector, double priceHardFactor) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", queryVector == null ? null : vectorLiteral(queryVector), Types.VARCHAR)
                .addValue("subjectId", context.subjectId())
                .addValue("gradeId", context.gradeId())
                .addValue("learningMode", context.learningMode().name())
                .addValue("areaScope", context.areaScope().name())
                .addValue("districtIds", uuidArrayLiteral(context.districtIds()), Types.VARCHAR)
                .addValue("provinceId", context.provinceId() == null ? null : context.provinceId().toString(),
                        Types.VARCHAR)
                .addValue("maxPrice", context.maxPrice(), Types.NUMERIC)
                .addValue("priceHardFactor", BigDecimal.valueOf(priceHardFactor))
                .addValue("studentId", context.studentId())
                .addValue("hideRejectedSince",
                        Timestamp.valueOf(LocalDateTime.now().minusDays(HIDE_REJECTED_POSTING_DAYS)));
        return jdbc.query(HARD_FILTER, params, (row, index) -> new Hit(
                row.getObject("teaching_request_id", UUID.class),
                row.getTimestamp("created_at").toLocalDateTime(),
                row.getObject("cosine") == null ? null : row.getDouble("cosine")));
    }

    /** Loads the scoring data of the given postings, in the order of {@code hits}. */
    public List<TeachingCandidate> load(UUID studentId, List<Hit> hits, int historyDays) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = hits.stream().map(Hit::teachingRequestId).toList();
        MapSqlParameterSource byIds = new MapSqlParameterSource("ids", ids);

        Map<UUID, Map<String, Object>> rows = new HashMap<>();
        jdbc.queryForList("""
                SELECT tr.id, tr.tutor_id, t.user_id, tr.title, tr.expected_price, tr.teaching_mode, tr.quantity,
                       (SELECT count(*) FROM student_tutor_requests s WHERE s.teaching_request_id = tr.id
                           AND s.deleted_at IS NULL AND s.status = 'ACCEPTED') AS accepted_count,
                       (SELECT count(*) FROM student_tutor_requests s WHERE s.teaching_request_id = tr.id
                           AND s.deleted_at IS NULL AND s.status = 'PENDING') AS pending_count,
                       t.recommendation_score, t.average_rating, t.total_reviews,
                       u.first_name, u.last_name, u.avatar_url
                FROM teaching_requests tr
                JOIN tutors t ON t.id = tr.tutor_id
                JOIN users u ON u.id = t.user_id
                WHERE tr.id IN (:ids)
                """, byIds).forEach(row -> rows.put((UUID) row.get("id"), row));

        Map<UUID, List<District>> districts = new HashMap<>();
        jdbc.query("""
                SELECT trd.teaching_request_id, d.id, d.name, d.province_id
                FROM teaching_request_districts trd
                JOIN districts d ON d.id = trd.district_id
                WHERE trd.teaching_request_id IN (:ids) AND trd.deleted_at IS NULL
                ORDER BY d.name
                """, byIds, row -> {
            districts.computeIfAbsent(row.getObject("teaching_request_id", UUID.class), key -> new ArrayList<>())
                    .add(new District(row.getObject("id", UUID.class), row.getString("name"),
                            row.getObject("province_id", UUID.class)));
        });

        Map<UUID, List<TimeSlot>> availabilities = new HashMap<>();
        jdbc.query("""
                SELECT teaching_request_id, day_of_week, start_time, end_time
                FROM teaching_request_availabilities
                WHERE teaching_request_id IN (:ids) AND deleted_at IS NULL
                ORDER BY day_of_week, start_time
                """, byIds, row -> {
            availabilities.computeIfAbsent(row.getObject("teaching_request_id", UUID.class), key -> new ArrayList<>())
                    .add(new TimeSlot(row.getInt("day_of_week"), row.getTime("start_time").toLocalTime(),
                            row.getTime("end_time").toLocalTime()));
        });

        Set<UUID> tutorIds = rows.values().stream().map(row -> (UUID) row.get("tutor_id")).collect(Collectors.toSet());
        Map<UUID, List<TagRow>> tags = loadTags(ids, tutorIds);
        Set<UUID> rejecting = new HashSet<>(jdbc.query("""
                SELECT DISTINCT tr.tutor_id
                FROM student_tutor_requests s
                JOIN teaching_requests tr ON tr.id = s.teaching_request_id
                WHERE s.student_id = :studentId AND s.deleted_at IS NULL AND s.status = 'REJECTED'
                  AND COALESCE(s.responded_at, s.updated_at) > :since
                  AND tr.tutor_id IN (:tutorIds)
                """, new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("tutorIds", tutorIds)
                        .addValue("since", Timestamp.valueOf(LocalDateTime.now().minusDays(RECENT_REJECTION_DAYS))),
                (row, index) -> row.getObject("tutor_id", UUID.class)));

        Map<UUID, List<PastDecision>> decisions = tutorDecisions(tutorIds, historyDays);
        Set<UUID> inviting = new HashSet<>(jdbc.query("""
                SELECT DISTINCT p.tutor_id
                FROM tutor_student_requests p
                JOIN studying_requests sr ON sr.id = p.studying_request_id
                WHERE p.deleted_at IS NULL AND p.status = 'PENDING'
                  AND sr.student_id = :studentId AND sr.status = 'OPEN' AND sr.deleted_at IS NULL
                  AND p.tutor_id IN (:tutorIds)
                """, new MapSqlParameterSource().addValue("studentId", studentId).addValue("tutorIds", tutorIds),
                (row, index) -> row.getObject("tutor_id", UUID.class)));

        List<TeachingCandidate> candidates = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            Map<String, Object> row = rows.get(hit.teachingRequestId());
            if (row == null) {
                continue;
            }
            UUID tutorId = (UUID) row.get("tutor_id");
            List<TagRow> candidateTags = new ArrayList<>(tags.getOrDefault(hit.teachingRequestId(), List.of()));
            candidateTags.addAll(tags.getOrDefault(tutorId, List.of()));
            candidates.add(new TeachingCandidate(
                    hit.teachingRequestId(), tutorId, (UUID) row.get("user_id"), (String) row.get("title"),
                    (BigDecimal) row.get("expected_price"), TeachingMode.valueOf((String) row.get("teaching_mode")),
                    hit.createdAt(), hit.cosine(), (BigDecimal) row.get("recommendation_score"),
                    displayName((String) row.get("first_name"), (String) row.get("last_name")),
                    (String) row.get("avatar_url"), (BigDecimal) row.get("average_rating"),
                    (Integer) row.get("total_reviews"),
                    districts.getOrDefault(hit.teachingRequestId(), List.of()),
                    availabilities.getOrDefault(hit.teachingRequestId(), List.of()),
                    candidateTags.stream()
                            .filter(tag -> tag.role().equals("STRENGTH")
                                    && (tag.group().equals("TOPIC") || tag.group().equals("GOAL")))
                            .map(tag -> new CandidateTag(tag.tagId(), tag.parentId()))
                            .collect(Collectors.toSet()),
                    codes(candidateTags, "OFFERED", "STYLE"),
                    codes(candidateTags, "TARGET", "SEGMENT"),
                    rejecting.contains(tutorId),
                    Math.max(1, (row.get("quantity") == null ? 1 : (Integer) row.get("quantity"))
                            - ((Number) row.get("accepted_count")).intValue()),
                    ((Number) row.get("pending_count")).intValue(),
                    decisions.getOrDefault(tutorId, List.of()),
                    inviting.contains(tutorId)));
        }
        return candidates;
    }

    /**
     * Share of applications that tutors accepted, over the whole platform. Used as the prior for a
     * tutor with little history; the default applies until there are enough decisions to measure it.
     */
    public double platformAcceptanceRate(int historyDays, int minDecisions, double defaultRate) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status = 'ACCEPTED') AS accepted, count(*) AS decided
                FROM student_tutor_requests
                WHERE deleted_at IS NULL AND status IN ('ACCEPTED', 'REJECTED')
                  AND COALESCE(responded_at, updated_at) > :since
                """, new MapSqlParameterSource("since",
                        Timestamp.valueOf(LocalDateTime.now().minusDays(historyDays))));
        long decided = ((Number) row.get("decided")).longValue();
        return decided < minDecisions ? defaultRate : ((Number) row.get("accepted")).doubleValue() / decided;
    }

    /** Tags of the postings and of their tutors, keyed by the id of the entity they are attached to. */
    private Map<UUID, List<TagRow>> loadTags(Collection<UUID> teachingRequestIds, Collection<UUID> tutorIds) {
        Map<UUID, List<TagRow>> tags = new HashMap<>();
        jdbc.query("""
                SELECT et.entity_id, et.role, tag.id, tag.parent_id, tag.code, tag.tag_group
                FROM recommendation_entity_tags et
                JOIN recommendation_tags tag ON tag.id = et.tag_id AND tag.deleted_at IS NULL
                WHERE et.deleted_at IS NULL AND tag.status = 'ACTIVE'
                  AND ((et.entity_type = 'TEACHING_REQUEST' AND et.entity_id IN (:teachingRequestIds))
                    OR (et.entity_type = 'TUTOR' AND et.entity_id IN (:tutorIds)))
                """ + llmTagFilter(), new MapSqlParameterSource()
                        .addValue("teachingRequestIds", teachingRequestIds)
                        .addValue("tutorIds", tutorIds),
                row -> {
                    tags.computeIfAbsent(row.getObject("entity_id", UUID.class), key -> new ArrayList<>())
                            .add(new TagRow(row.getString("role"), row.getObject("id", UUID.class),
                                    row.getObject("parent_id", UUID.class), row.getString("code"),
                                    row.getString("tag_group")));
                });
        return tags;
    }

    /** Extra condition on {@code et} that drops LLM tags while they are switched off. */
    private String llmTagFilter() {
        return settings.isLlmTagsEnabled() ? "" : " AND et.source <> 'LLM'";
    }

    private Set<String> codes(List<TagRow> tags, String role, String group) {
        return tags.stream()
                .filter(tag -> tag.role().equals(role) && tag.group().equals(group))
                .map(TagRow::code)
                .collect(Collectors.toSet());
    }

    private String displayName(String firstName, String lastName) {
        return java.util.stream.Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(" "));
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(vector[index]);
        }
        return literal.append(']').toString();
    }

    private String uuidArrayLiteral(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    /** What each tutor decided about student applications in the last {@code historyDays} (input of b_hist). */
    public Map<UUID, List<PastDecision>> tutorDecisions(Collection<UUID> tutorIds, int historyDays) {
        Map<UUID, List<PastDecision>> decisions = new HashMap<>();
        if (tutorIds.isEmpty()) {
            return decisions;
        }
        jdbc.query("""
                SELECT tr.tutor_id, g.level, s.proposed_price, s.learning_mode, s.status
                FROM student_tutor_requests s
                JOIN teaching_requests tr ON tr.id = s.teaching_request_id
                LEFT JOIN grades g ON g.id = s.grade_id
                WHERE s.deleted_at IS NULL AND s.status IN ('ACCEPTED', 'REJECTED')
                  AND COALESCE(s.responded_at, s.updated_at) > :since
                  AND tr.tutor_id IN (:tutorIds)
                """, new MapSqlParameterSource()
                        .addValue("tutorIds", tutorIds)
                        .addValue("since", Timestamp.valueOf(LocalDateTime.now().minusDays(historyDays))),
                row -> {
                    decisions.computeIfAbsent(row.getObject("tutor_id", UUID.class), key -> new ArrayList<>())
                            .add(new PastDecision(
                                    (Integer) row.getObject("level"), row.getBigDecimal("proposed_price"),
                                    LearningMode.valueOf(row.getString("learning_mode")),
                                    row.getString("status").equals("ACCEPTED")));
                });
        return decisions;
    }

    public record Hit(UUID teachingRequestId, LocalDateTime createdAt, Double cosine) {
    }

    private record TagRow(String role, UUID tagId, UUID parentId, String code, String group) {
    }
}
