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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate.PastProposal;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.repository.CandidateRepository.Hit;

import lombok.RequiredArgsConstructor;

/**
 * Candidate retrieval for "tutor looks for students" (design 06, 6.12): the mirror image of
 * {@link CandidateRepository}, over OPEN studying postings.
 */
@Repository
@RequiredArgsConstructor
public class StudyingCandidateRepository {

    private static final String HARD_FILTER = """
            SELECT sr.id AS studying_request_id,
                   sr.created_at,
                   1 - (sr.embedding <=> CAST(:queryVector AS vector)) AS cosine
            FROM studying_requests sr
            JOIN students st ON st.id = sr.student_id AND st.deleted_at IS NULL
            JOIN users u ON u.id = st.user_id AND u.deleted_at IS NULL
            WHERE sr.deleted_at IS NULL
              AND sr.status = 'OPEN'
              AND u.status = 'ACTIVE'
              AND sr.subject_id = :subjectId
              AND sr.grade_id = ANY(CAST(:gradeIds AS uuid[]))
              AND (:teachingMode = 'ANY' OR sr.learning_mode = :teachingMode)
              AND (sr.learning_mode = 'ONLINE'
                   OR :areaScope = 'ANY'
                   OR (:areaScope = 'DISTRICTS' AND sr.district_id = ANY(CAST(:districtIds AS uuid[])))
                   OR (:areaScope = 'PROVINCE' AND EXISTS (
                           SELECT 1 FROM districts d
                           WHERE d.id = sr.district_id AND d.province_id = CAST(:provinceId AS uuid))))
              AND (CAST(:expectedPrice AS numeric) IS NULL OR sr.max_price IS NULL
                   OR CAST(:expectedPrice AS numeric) <= sr.max_price * :priceHardFactor)
              AND NOT EXISTS (SELECT 1 FROM contracts c
                              WHERE c.tutor_id = :tutorId AND c.student_id = sr.student_id
                                AND c.subject_id = sr.subject_id AND c.status IN ('PENDING', 'ACTIVE')
                                AND c.deleted_at IS NULL)
              AND NOT EXISTS (SELECT 1 FROM tutor_student_requests p
                              WHERE p.tutor_id = :tutorId AND p.studying_request_id = sr.id
                                AND p.deleted_at IS NULL
                                AND (p.status IN ('PENDING', 'ACCEPTED')
                                     OR (p.status = 'REJECTED'
                                         AND COALESCE(p.responded_at, p.updated_at) > :hideRejectedSince)))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Studying postings that pass every hard filter, with their cosine to the supply vector. */
    public List<Hit> findHits(SupplyContext context, float[] queryVector, double priceHardFactor) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", queryVector == null ? null : vectorLiteral(queryVector), Types.VARCHAR)
                .addValue("subjectId", context.subjectId())
                .addValue("gradeIds", uuidArrayLiteral(context.gradeIds()), Types.VARCHAR)
                .addValue("teachingMode", context.teachingMode().name())
                .addValue("areaScope", context.areaScope().name())
                .addValue("districtIds", uuidArrayLiteral(context.districtIds()), Types.VARCHAR)
                .addValue("provinceId", context.provinceId() == null ? null : context.provinceId().toString(),
                        Types.VARCHAR)
                .addValue("expectedPrice", context.expectedPrice(), Types.NUMERIC)
                .addValue("priceHardFactor", BigDecimal.valueOf(priceHardFactor))
                .addValue("tutorId", context.tutorId())
                .addValue("hideRejectedSince", Timestamp.valueOf(
                        LocalDateTime.now().minusDays(CandidateRepository.HIDE_REJECTED_POSTING_DAYS)));
        return jdbc.query(HARD_FILTER, params, (row, index) -> new Hit(
                row.getObject("studying_request_id", UUID.class),
                row.getTimestamp("created_at").toLocalDateTime(),
                row.getObject("cosine") == null ? null : row.getDouble("cosine")));
    }

    /** Loads the scoring data of the given postings, in the order of {@code hits}. */
    public List<StudyingCandidate> load(UUID tutorId, List<Hit> hits, int historyDays) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = hits.stream().map(Hit::teachingRequestId).toList();
        MapSqlParameterSource byIds = new MapSqlParameterSource("ids", ids);

        Map<UUID, Map<String, Object>> rows = new HashMap<>();
        jdbc.queryForList("""
                SELECT sr.id, sr.student_id, st.user_id, sr.title, sr.learning_goals, sr.min_price, sr.max_price,
                       sr.learning_mode, g.name AS grade_name,
                       d.id AS district_id, d.name AS district_name, d.province_id,
                       (SELECT count(*) FROM tutor_student_requests p WHERE p.studying_request_id = sr.id
                           AND p.deleted_at IS NULL AND p.status = 'PENDING') AS pending_count
                FROM studying_requests sr
                JOIN students st ON st.id = sr.student_id
                LEFT JOIN grades g ON g.id = sr.grade_id
                LEFT JOIN districts d ON d.id = sr.district_id
                WHERE sr.id IN (:ids)
                """, byIds).forEach(row -> rows.put((UUID) row.get("id"), row));

        Map<UUID, List<TimeSlot>> availabilities = new HashMap<>();
        jdbc.query("""
                SELECT studying_request_id, day_of_week, start_time, end_time
                FROM studying_request_availabilities
                WHERE studying_request_id IN (:ids) AND deleted_at IS NULL
                ORDER BY day_of_week, start_time
                """, byIds, row -> {
            availabilities.computeIfAbsent(row.getObject("studying_request_id", UUID.class), key -> new ArrayList<>())
                    .add(new TimeSlot(row.getInt("day_of_week"), row.getTime("start_time").toLocalTime(),
                            row.getTime("end_time").toLocalTime()));
        });

        Set<UUID> studentIds = rows.values().stream()
                .map(row -> (UUID) row.get("student_id")).collect(Collectors.toSet());
        MapSqlParameterSource byStudents = new MapSqlParameterSource()
                .addValue("tutorId", tutorId)
                .addValue("studentIds", studentIds)
                .addValue("ids", ids);

        Map<UUID, List<TagRow>> tags = new HashMap<>();
        jdbc.query("""
                SELECT et.entity_id, et.role, et.confidence, tag.id, tag.parent_id, tag.code, tag.tag_group
                FROM recommendation_entity_tags et
                JOIN recommendation_tags tag ON tag.id = et.tag_id AND tag.deleted_at IS NULL
                WHERE et.deleted_at IS NULL AND tag.status = 'ACTIVE'
                  AND ((et.entity_type = 'STUDYING_REQUEST' AND et.entity_id IN (:ids))
                    OR (et.entity_type = 'STUDENT' AND et.entity_id IN (:studentIds)))
                """, byStudents, row -> {
            tags.computeIfAbsent(row.getObject("entity_id", UUID.class), key -> new ArrayList<>())
                    .add(new TagRow(row.getString("role"), row.getObject("id", UUID.class),
                            row.getObject("parent_id", UUID.class), row.getString("code"),
                            row.getString("tag_group"), row.getDouble("confidence")));
        });

        Set<UUID> rejecting = new HashSet<>(jdbc.query("""
                SELECT DISTINCT sr.student_id
                FROM tutor_student_requests p
                JOIN studying_requests sr ON sr.id = p.studying_request_id
                WHERE p.tutor_id = :tutorId AND p.deleted_at IS NULL AND p.status = 'REJECTED'
                  AND COALESCE(p.responded_at, p.updated_at) > :since
                  AND sr.student_id IN (:studentIds)
                """, byStudents.addValue("since", Timestamp.valueOf(
                        LocalDateTime.now().minusDays(CandidateRepository.RECENT_REJECTION_DAYS))),
                (row, index) -> row.getObject("student_id", UUID.class)));

        Map<UUID, List<PastProposal>> decisions = studentDecisions(studentIds, historyDays);

        Set<UUID> applied = new HashSet<>(jdbc.query("""
                SELECT DISTINCT s.student_id
                FROM student_tutor_requests s
                JOIN teaching_requests tr ON tr.id = s.teaching_request_id
                WHERE s.deleted_at IS NULL AND s.status = 'PENDING'
                  AND tr.tutor_id = :tutorId AND tr.status = 'OPEN' AND tr.deleted_at IS NULL
                  AND s.student_id IN (:studentIds)
                """, byStudents, (row, index) -> row.getObject("student_id", UUID.class)));

        List<StudyingCandidate> candidates = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            Map<String, Object> row = rows.get(hit.teachingRequestId());
            if (row == null) {
                continue;
            }
            UUID studentId = (UUID) row.get("student_id");
            List<TagRow> candidateTags = new ArrayList<>(tags.getOrDefault(hit.teachingRequestId(), List.of()));
            candidateTags.addAll(tags.getOrDefault(studentId, List.of()));
            UUID districtId = (UUID) row.get("district_id");
            candidates.add(new StudyingCandidate(
                    hit.teachingRequestId(), studentId, (UUID) row.get("user_id"), (String) row.get("title"),
                    (String) row.get("learning_goals"), (BigDecimal) row.get("min_price"),
                    (BigDecimal) row.get("max_price"), LearningMode.valueOf((String) row.get("learning_mode")),
                    (String) row.get("grade_name"),
                    districtId == null ? null : new District(
                            districtId, (String) row.get("district_name"), (UUID) row.get("province_id")),
                    hit.createdAt(), hit.cosine(),
                    availabilities.getOrDefault(hit.teachingRequestId(), List.of()),
                    candidateTags.stream()
                            .filter(tag -> (tag.role().equals("NEED") || tag.role().equals("SELF"))
                                    && (tag.group().equals("TOPIC") || tag.group().equals("GOAL")))
                            .map(tag -> new WeightedTag(tag.tagId(), tag.code(), tag.parentId(), tag.confidence()))
                            .toList(),
                    candidateTags.stream()
                            .filter(tag -> tag.role().equals("PREFERRED") && tag.group().equals("STYLE"))
                            .map(TagRow::code).collect(Collectors.toSet()),
                    candidateTags.stream()
                            .filter(tag -> tag.role().equals("SELF") && tag.group().equals("SEGMENT"))
                            .map(TagRow::code).findFirst().orElse(null),
                    rejecting.contains(studentId),
                    // A studying posting takes exactly one tutor and becomes MATCHED when it has one,
                    // so an OPEN posting always has one place left.
                    1,
                    ((Number) row.get("pending_count")).intValue(),
                    decisions.getOrDefault(studentId, List.of()),
                    applied.contains(studentId)));
        }
        return candidates;
    }

    /** Share of tutor proposals that students accepted, over the whole platform. */
    /** What each student decided about tutor proposals in the last {@code historyDays} (input of b'_hist). */
    public Map<UUID, List<PastProposal>> studentDecisions(Collection<UUID> studentIds, int historyDays) {
        Map<UUID, List<PastProposal>> decisions = new HashMap<>();
        if (studentIds.isEmpty()) {
            return decisions;
        }
        jdbc.query("""
                SELECT sr.student_id, p.proposed_price, t.recommendation_score, p.status
                FROM tutor_student_requests p
                JOIN studying_requests sr ON sr.id = p.studying_request_id
                JOIN tutors t ON t.id = p.tutor_id
                WHERE p.deleted_at IS NULL AND p.status IN ('ACCEPTED', 'REJECTED')
                  AND COALESCE(p.responded_at, p.updated_at) > :historySince
                  AND sr.student_id IN (:studentIds)
                """, new MapSqlParameterSource()
                        .addValue("studentIds", studentIds)
                        .addValue("historySince", Timestamp.valueOf(LocalDateTime.now().minusDays(historyDays))),
                row -> {
                    BigDecimal score = row.getBigDecimal("recommendation_score");
                    decisions.computeIfAbsent(row.getObject("student_id", UUID.class), key -> new ArrayList<>())
                            .add(new PastProposal(row.getBigDecimal("proposed_price"),
                                    score == null ? null : score.doubleValue() / 100.0,
                                    row.getString("status").equals("ACCEPTED")));
                });
        return decisions;
    }

    public double platformAcceptanceRate(int historyDays, int minDecisions, double defaultRate) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status = 'ACCEPTED') AS accepted, count(*) AS decided
                FROM tutor_student_requests
                WHERE deleted_at IS NULL AND status IN ('ACCEPTED', 'REJECTED')
                  AND COALESCE(responded_at, updated_at) > :since
                """, new MapSqlParameterSource("since",
                        Timestamp.valueOf(LocalDateTime.now().minusDays(historyDays))));
        long decided = ((Number) row.get("decided")).longValue();
        return decided < minDecisions ? defaultRate : ((Number) row.get("accepted")).doubleValue() / decided;
    }

    /** Median asking price of the tutor's own teaching postings: the default expected price. */
    public Optional<BigDecimal> medianExpectedPrice(UUID tutorId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT CAST(percentile_cont(0.5) WITHIN GROUP (ORDER BY expected_price) AS numeric(15, 2))
                FROM teaching_requests
                WHERE tutor_id = :tutorId AND deleted_at IS NULL AND expected_price IS NOT NULL
                """, new MapSqlParameterSource("tutorId", tutorId), BigDecimal.class));
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

    private record TagRow(String role, UUID tagId, UUID parentId, String code, String group, double confidence) {
    }
}
