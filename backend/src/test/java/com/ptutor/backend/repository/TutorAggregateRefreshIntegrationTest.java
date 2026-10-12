package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the aggregate refresh statement against PostgreSQL. Every row is created inside the
 * test transaction and rolled back afterwards.
 */
@SpringBootTest
@Transactional
class TutorAggregateRefreshIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 8, 0);

    @Autowired TutorRepository tutorRepository;
    @Autowired JdbcTemplate jdbc;

    private UUID tutorId;
    private UUID otherTutorId;
    private UUID subjectId;
    private UUID gradeId;
    private UUID teachingRequestId;

    @BeforeEach
    void setUp() {
        tutorId = insertTutor();
        otherTutorId = insertTutor();
        subjectId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')",
                subjectId, "subject-" + subjectId);
        jdbc.update("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')", gradeId, "grade-" + gradeId);
        teachingRequestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status)
                VALUES (?, ?, 'ONLINE', 'OPEN')
                """, teachingRequestId, tutorId);
    }

    @Test
    void computesEveryAggregateFromSourceTables() {
        UUID firstStudent = insertStudent();
        UUID secondStudent = insertStudent();
        UUID thirdStudent = insertStudent();

        // Applications decided by the tutor; a fourth accepted one is added with the cancelled contract below.
        UUID firstAccepted = insertRequest(firstStudent, "ACCEPTED", CREATED_AT.plusHours(2), null);
        UUID secondAccepted = insertRequest(secondStudent, "ACCEPTED", CREATED_AT.plusHours(4), null);
        UUID thirdAccepted = insertRequest(thirdStudent, "ACCEPTED", CREATED_AT.plusHours(6), null);
        // Processed before V25: responded_at is missing, updated_at is used instead.
        insertRequest(firstStudent, "REJECTED", null, CREATED_AT.plusHours(12));
        // Not decisions of the tutor: ignored.
        insertRequest(secondStudent, "PENDING", null, CREATED_AT.plusHours(100));
        insertRequest(thirdStudent, "CANCELLED", null, CREATED_AT.plusHours(100));

        // Two completed contracts, students taught = 2 distinct (ACTIVE or COMPLETED).
        UUID completed = insertContract(firstStudent, firstAccepted, "COMPLETED");
        insertContract(firstStudent, secondAccepted, "COMPLETED");
        insertContract(secondStudent, thirdAccepted, "ACTIVE");
        insertContract(thirdStudent, insertRequest(thirdStudent, "ACCEPTED", CREATED_AT.plusHours(6), null),
                "CANCELLED");

        insertReview(firstStudent, completed, 5, false);
        insertReview(firstStudent, null, 4, false);
        insertReview(secondStudent, null, 2, false);
        insertReview(secondStudent, null, 1, true);

        int updated = tutorRepository.refreshAggregates(tutorId);

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = aggregates(tutorId);
        assertThat((BigDecimal) row.get("average_rating")).isEqualByComparingTo("3.67");
        assertThat(row.get("total_reviews")).isEqualTo(3);
        assertThat(row.get("completed_contracts_count")).isEqualTo(2);
        assertThat(row.get("total_students_taught")).isEqualTo(2);
        // 4 accepted (three above + the one behind the cancelled contract) out of 5 decided.
        assertThat((BigDecimal) row.get("acceptance_rate")).isEqualByComparingTo("80.00");
        // (2 + 4 + 6 + 12 + 6) / 5
        assertThat((BigDecimal) row.get("avg_response_time_hours")).isEqualByComparingTo("6.00");
    }

    @Test
    void leavesAcceptanceRateEmptyBelowThreeDecisions() {
        UUID student = insertStudent();
        insertRequest(student, "ACCEPTED", CREATED_AT.plusHours(3), null);
        insertRequest(student, "REJECTED", CREATED_AT.plusHours(5), null);

        tutorRepository.refreshAggregates(tutorId);

        Map<String, Object> row = aggregates(tutorId);
        assertThat(row.get("acceptance_rate")).isNull();
        assertThat((BigDecimal) row.get("avg_response_time_hours")).isEqualByComparingTo("4.00");
    }

    @Test
    void countsNegativeResponseTimeAsZero() {
        UUID student = insertStudent();
        insertRequest(student, "ACCEPTED", CREATED_AT.minusHours(7), null);
        insertRequest(student, "REJECTED", CREATED_AT.plusHours(4), null);

        tutorRepository.refreshAggregates(tutorId);

        assertThat((BigDecimal) aggregates(tutorId).get("avg_response_time_hours")).isEqualByComparingTo("2.00");
    }

    @Test
    void resetsTutorWithoutAnyActivity() {
        jdbc.update("""
                UPDATE tutors SET average_rating = 4.5, total_reviews = 9, completed_contracts_count = 7,
                       total_students_taught = 6, acceptance_rate = 90, avg_response_time_hours = 3
                WHERE id = ?
                """, otherTutorId);

        tutorRepository.refreshAggregates(otherTutorId);

        Map<String, Object> row = aggregates(otherTutorId);
        assertThat((BigDecimal) row.get("average_rating")).isEqualByComparingTo("0");
        assertThat(row.get("total_reviews")).isEqualTo(0);
        assertThat(row.get("completed_contracts_count")).isEqualTo(0);
        assertThat(row.get("total_students_taught")).isEqualTo(0);
        assertThat(row.get("acceptance_rate")).isNull();
        assertThat(row.get("avg_response_time_hours")).isNull();
    }

    @Test
    void refreshingOneTutorLeavesOthersUntouched() {
        jdbc.update("UPDATE tutors SET total_reviews = 9 WHERE id = ?", otherTutorId);

        tutorRepository.refreshAggregates(tutorId);

        assertThat(aggregates(otherTutorId).get("total_reviews")).isEqualTo(9);
    }

    @Test
    void refreshAllCoversEveryTutor() {
        UUID student = insertStudent();
        insertReview(student, null, 4, false);
        jdbc.update("UPDATE tutors SET total_reviews = 9 WHERE id = ?", otherTutorId);

        int updated = tutorRepository.refreshAllAggregates();

        assertThat(updated).isGreaterThanOrEqualTo(2);
        assertThat(aggregates(tutorId).get("total_reviews")).isEqualTo(1);
        assertThat(aggregates(otherTutorId).get("total_reviews")).isEqualTo(0);
    }

    private Map<String, Object> aggregates(UUID id) {
        return jdbc.queryForMap("""
                SELECT average_rating, total_reviews, completed_contracts_count, total_students_taught,
                       acceptance_rate, avg_response_time_hours
                FROM tutors WHERE id = ?
                """, id);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
                """, id, "cid-" + id, id + "@example.com");
        return id;
    }

    private UUID insertTutor() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", id, insertUser());
        return id;
    }

    private UUID insertStudent() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO students (id, user_id) VALUES (?, ?)", id, insertUser());
        return id;
    }

    private UUID insertRequest(UUID studentId, String status, LocalDateTime respondedAt, LocalDateTime updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO student_tutor_requests
                    (id, student_id, grade_id, teaching_request_id, learning_mode, status,
                     created_at, updated_at, responded_at)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?)
                """, id, studentId, gradeId, teachingRequestId, status,
                CREATED_AT, updatedAt == null ? CREATED_AT.plusDays(30) : updatedAt, respondedAt);
        return id;
    }

    private UUID insertContract(UUID studentId, UUID studentTutorRequestId, String status) {
        UUID id = UUID.randomUUID();
        UUID creator = jdbc.queryForObject("SELECT user_id FROM students WHERE id = ?", UUID.class, studentId);
        jdbc.update("""
                INSERT INTO contracts
                    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period,
                     total_lession, start_date, end_date, status, created_by_user_id, preferred_schedule,
                     student_tutor_request_id)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 100000, 'MONTHLY', 8, DATE '2026-06-01', DATE '2026-08-01',
                        ?, ?, 'Monday evening', ?)
                """, id, studentId, tutorId, subjectId, gradeId, status, creator, studentTutorRequestId);
        return id;
    }

    private void insertReview(UUID studentId, UUID contractId, int rating, boolean deleted) {
        jdbc.update("""
                INSERT INTO reviews (id, student_id, tutor_id, contract_id, rating, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), studentId, tutorId, contractId, rating,
                deleted ? CREATED_AT : null);
    }
}
