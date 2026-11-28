package com.ptutor.backend.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.entity.enums.ComplaintCategory;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;

import lombok.RequiredArgsConstructor;

/**
 * Inputs of the complaint penalty in the tutor quality score. Kept on the recommendation side so the complaint
 * feature's repository is not changed.
 */
@Repository
@RequiredArgsConstructor
public class TutorComplaintPenaltyRepository {

    /**
     * A concluded complaint the tutor was found at fault for.
     *
     * @param category the category the complainant chose, null for complaints concluded before categories
     * @param sentByTutor the tutor sent the complaint and was found at fault as the complainant
     */
    public record FaultComplaint(ComplaintCategory category, ComplaintFaultParty faultParty, boolean sentByTutor) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Complaints on the tutor's contracts, resolved since {@code since}, where the tutor is at fault: as the
     * respondent (the student sent it) with fault RESPONDENT or BOTH, or as the complainant with fault COMPLAINANT
     * or BOTH. The respondent is decided like {@code ComplaintParties.respondentOf}.
     */
    public List<FaultComplaint> findFaultComplaints(UUID tutorId, LocalDateTime since) {
        return jdbc.query("""
                SELECT c.category, c.fault_party, c.user_id = tu.user_id AS sent_by_tutor
                FROM complaints c
                JOIN contracts ct ON ct.id = c.contract_id
                JOIN tutors tu ON tu.id = ct.tutor_id
                JOIN students st ON st.id = ct.student_id
                WHERE ct.tutor_id = :tutorId
                  AND c.status = 'RESOLVED'
                  AND c.deleted_at IS NULL
                  AND c.resolved_at >= :since
                  AND ((c.user_id = st.user_id AND c.fault_party IN ('RESPONDENT', 'BOTH'))
                    OR (c.user_id = tu.user_id AND c.fault_party IN ('COMPLAINANT', 'BOTH')))
                """,
                new MapSqlParameterSource()
                        .addValue("tutorId", tutorId)
                        .addValue("since", Timestamp.valueOf(since)),
                (rs, rowNum) -> new FaultComplaint(
                        rs.getString("category") == null ? null : ComplaintCategory.valueOf(rs.getString("category")),
                        ComplaintFaultParty.valueOf(rs.getString("fault_party")),
                        rs.getBoolean("sent_by_tutor")));
    }

    /**
     * Contracts that were signed, not deleted. Counted by {@code signed_at}, not by status: a contract rejected or
     * expired before signing also ends up CANCELLED.
     */
    public long countSignedContracts(UUID tutorId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM contracts
                WHERE tutor_id = :tutorId
                  AND signed_at IS NOT NULL
                  AND deleted_at IS NULL
                """, new MapSqlParameterSource("tutorId", tutorId), Long.class);
        return count == null ? 0 : count;
    }
}
