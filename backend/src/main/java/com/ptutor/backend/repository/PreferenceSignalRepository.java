package com.ptutor.backend.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.entity.enums.RecommendationCandidateType;

import lombok.RequiredArgsConstructor;

/**
 * What a user did with postings of the other side (design 07, 7.4), and the vectors and tags of
 * those postings. Read-only SQL shared by the preference profile, the exploration slots and the
 * in-session refinement.
 */
@Repository
@RequiredArgsConstructor
public class PreferenceSignalRepository {

    /** Clicks on search results. The same action on the same posting counts once, at its latest time. */
    private static final String STUDENT_CLICKS = """
            SELECT cl.candidate_teaching_request_id AS candidate_id, cl.action AS kind, MAX(cl.created_at) AS happened_at
            FROM recommendation_click_logs cl
            JOIN recommendation_logs l ON l.id = cl.recommendation_log_id
            WHERE l.searcher_student_id = :id AND cl.deleted_at IS NULL
              AND cl.candidate_teaching_request_id IS NOT NULL AND cl.created_at > :since
            GROUP BY cl.candidate_teaching_request_id, cl.action
            """;

    private static final String TUTOR_CLICKS = """
            SELECT cl.candidate_studying_request_id AS candidate_id, cl.action AS kind, MAX(cl.created_at) AS happened_at
            FROM recommendation_click_logs cl
            JOIN recommendation_logs l ON l.id = cl.recommendation_log_id
            WHERE l.searcher_tutor_id = :id AND cl.deleted_at IS NULL
              AND cl.candidate_studying_request_id IS NOT NULL AND cl.created_at > :since
            GROUP BY cl.candidate_studying_request_id, cl.action
            """;

    /** Applications the student sent; an accepted one is a stronger signal than one merely sent. */
    private static final String STUDENT_REQUESTS = """
            SELECT r.teaching_request_id AS candidate_id,
                   CASE WHEN r.status = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'REQUESTED' END AS kind,
                   r.created_at AS happened_at
            FROM student_tutor_requests r
            WHERE r.student_id = :id AND r.deleted_at IS NULL AND r.created_at > :since
            """;

    private static final String TUTOR_REQUESTS = """
            SELECT r.studying_request_id AS candidate_id,
                   CASE WHEN r.status = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'REQUESTED' END AS kind,
                   r.created_at AS happened_at
            FROM tutor_student_requests r
            WHERE r.tutor_id = :id AND r.deleted_at IS NULL AND r.created_at > :since
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** @param kind VIEWED, CLICKED, FAVORITED, CONTACTED, DISMISSED, REQUESTED or ACCEPTED */
    public record Signal(UUID candidateId, String kind, LocalDateTime happenedAt) {
    }

    /** Everything a student did with teaching postings since {@code since}. */
    public List<Signal> ofStudent(UUID studentId, LocalDateTime since) {
        return signals(STUDENT_CLICKS + " UNION ALL " + STUDENT_REQUESTS, studentId, since);
    }

    /** Everything a tutor did with studying postings since {@code since}. */
    public List<Signal> ofTutor(UUID tutorId, LocalDateTime since) {
        return signals(TUTOR_CLICKS + " UNION ALL " + TUTOR_REQUESTS, tutorId, since);
    }

    private List<Signal> signals(String sql, UUID id, LocalDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id).addValue("since", Timestamp.valueOf(since));
        return jdbc.query(sql, params, (row, index) -> new Signal(
                row.getObject("candidate_id", UUID.class), row.getString("kind"),
                row.getTimestamp("happened_at").toLocalDateTime()));
    }

    /** Vectors of the given postings; postings without a vector are left out. */
    public Map<UUID, float[]> embeddings(RecommendationCandidateType type, Collection<UUID> ids) {
        Map<UUID, float[]> vectors = new HashMap<>();
        if (ids.isEmpty()) {
            return vectors;
        }
        jdbc.query("SELECT id, CAST(embedding AS text) AS vector FROM " + table(type)
                        + " WHERE id IN (:ids) AND embedding IS NOT NULL",
                new MapSqlParameterSource("ids", ids), row -> {
                    vectors.put(row.getObject("id", UUID.class), parse(row.getString("vector")));
                });
        return vectors;
    }

    /** Codes of the active tags of each posting and of the person behind it. */
    public Map<UUID, Set<String>> tagCodes(RecommendationCandidateType type, Collection<UUID> ids) {
        Map<UUID, Set<String>> codes = new HashMap<>();
        if (ids.isEmpty()) {
            return codes;
        }
        boolean teaching = type == RecommendationCandidateType.TEACHING_REQUEST;
        String sql = "SELECT p.id, t.code FROM " + table(type) + " p"
                + " JOIN recommendation_entity_tags et ON et.deleted_at IS NULL AND ("
                + "(et.entity_type = '" + type.name() + "' AND et.entity_id = p.id) OR "
                + (teaching ? "(et.entity_type = 'TUTOR' AND et.entity_id = p.tutor_id)"
                        : "(et.entity_type = 'STUDENT' AND et.entity_id = p.student_id)")
                + ") JOIN recommendation_tags t ON t.id = et.tag_id AND t.status = 'ACTIVE'"
                + " WHERE p.id IN (:ids)";
        jdbc.query(sql, new MapSqlParameterSource("ids", ids), row -> {
            codes.computeIfAbsent(row.getObject("id", UUID.class), id -> new HashSet<>()).add(row.getString("code"));
        });
        return codes;
    }

    /** Cosine similarity of each posting's vector with {@code vector}; postings without a vector are left out. */
    public Map<UUID, Double> cosines(RecommendationCandidateType type, Collection<UUID> ids, float[] vector) {
        Map<UUID, Double> cosines = new HashMap<>();
        if (ids.isEmpty() || vector == null) {
            return cosines;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids).addValue("vector", literal(vector));
        jdbc.query("SELECT id, 1 - (embedding <=> CAST(:vector AS vector)) AS cosine FROM " + table(type)
                + " WHERE id IN (:ids) AND embedding IS NOT NULL", params, row -> {
                    cosines.put(row.getObject("id", UUID.class), row.getDouble("cosine"));
                });
        return cosines;
    }

    private String table(RecommendationCandidateType type) {
        return type == RecommendationCandidateType.TEACHING_REQUEST ? "teaching_requests" : "studying_requests";
    }

    /** pgvector text form: {@code [0.1,0.2,...]}. */
    public static float[] parse(String text) {
        String[] parts = text.substring(1, text.length() - 1).split(",");
        float[] vector = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            vector[index] = Float.parseFloat(parts[index]);
        }
        return vector;
    }

    public static String literal(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            text.append(index == 0 ? "" : ",").append(vector[index]);
        }
        return text.append(']').toString();
    }
}
