package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ptutor.backend.entity.enums.ComplaintFaultParty;

/**
 * An employee concludes a complaint with the tutor at fault: once the conclusion is committed, the tutor's
 * quality score is recalculated with the complaint penalty. The listener only runs after a real commit, so the
 * rows are committed and removed again afterwards.
 */
@SpringBootTest
class ComplaintPenaltyScoreIntegrationTest {

    @Autowired AdminComplaintService adminComplaintService;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> userIds = new ArrayList<>();
    private UUID subjectId;
    private UUID gradeId;
    private UUID tutorId;
    private UUID employeeUserId;
    private UUID complaintId;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", subjectId, "subject-" + subjectId);
        jdbc.update("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')", gradeId, "grade-" + gradeId);
        tutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", tutorId, insertUser());
        UUID studentUserId = insertUser();
        UUID studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO students (id, user_id) VALUES (?, ?)", studentId, studentUserId);
        employeeUserId = insertUser();
        UUID employeeId = UUID.randomUUID();
        jdbc.update("INSERT INTO employees (id, user_id, role, job_function) VALUES (?, ?, 2, 'COMPLAINT_HANDLER')",
                employeeId, employeeUserId);

        // A contract needs the application it came from.
        UUID teachingRequestId = UUID.randomUUID();
        jdbc.update("INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status) VALUES (?, ?, 'ONLINE', 'OPEN')",
                teachingRequestId, tutorId);
        UUID applicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'ACCEPTED')
                """, applicationId, studentId, gradeId, teachingRequestId);
        UUID contractId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contracts
                    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period,
                     total_lession, start_date, end_date, status, created_by_user_id, preferred_schedule,
                     student_tutor_request_id)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 100000, 'MONTHLY', 8, DATE '2026-06-01', DATE '2026-08-01',
                        'ACTIVE', ?, 'Monday evening', ?)
                """, contractId, studentId, tutorId, subjectId, gradeId, studentUserId, applicationId);
        complaintId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO complaints (id, user_id, contract_id, employee_id, title, content, status, category)
                VALUES (?, ?, ?, ?, 'Tutor did not come', 'Missed the lesson', 'IN_REVIEW', 'ABSENCE')
                """, complaintId, studentUserId, contractId, employeeId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notifications WHERE user_id = ANY (?::uuid[])", (Object) userIdArray());
        jdbc.update("DELETE FROM complaints WHERE id = ?", complaintId);
        jdbc.update("DELETE FROM contracts WHERE tutor_id = ?", tutorId);
        jdbc.update("DELETE FROM student_tutor_requests WHERE teaching_request_id IN "
                + "(SELECT id FROM teaching_requests WHERE tutor_id = ?)", tutorId);
        jdbc.update("DELETE FROM teaching_requests WHERE tutor_id = ?", tutorId);
        jdbc.update("DELETE FROM employees WHERE user_id = ?", employeeUserId);
        jdbc.update("DELETE FROM tutors WHERE id = ?", tutorId);
        jdbc.update("DELETE FROM students WHERE user_id = ANY (?::uuid[])", (Object) userIdArray());
        jdbc.update("DELETE FROM users WHERE id = ANY (?::uuid[])", (Object) userIdArray());
        jdbc.update("DELETE FROM subjects WHERE id = ?", subjectId);
        jdbc.update("DELETE FROM grades WHERE id = ?", gradeId);
    }

    @Test
    void concludingAComplaintWithTheTutorAtFaultLowersTheTutorScore() {
        adminComplaintService.accept(employeeUserId, complaintId, "The tutor missed the lesson",
                ComplaintFaultParty.RESPONDENT);

        Map<String, Object> tutor = jdbc.queryForMap("""
                SELECT score_formula_version,
                       (score_breakdown ->> 'complaintPenalty')::numeric AS penalty,
                       (score_breakdown ->> 'faultComplaints')::int AS complaints
                FROM tutors WHERE id = ?
                """, tutorId);
        assertThat(tutor.get("score_formula_version")).isEqualTo("v2");
        // One absence over one signed contract: 40 × 1 / (1 + 5).
        assertThat((java.math.BigDecimal) tutor.get("penalty")).isEqualByComparingTo("6.67");
        assertThat(tutor.get("complaints")).isEqualTo(1);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
                """, id, "cid-" + id, id + "@example.com");
        userIds.add(id);
        return id;
    }

    private String userIdArray() {
        return userIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
