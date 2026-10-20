package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.ComplaintCategory;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;
import com.ptutor.backend.repository.TutorComplaintPenaltyRepository.FaultComplaint;

/**
 * Runs the complaint penalty queries against PostgreSQL. Every row is created inside the test transaction and
 * rolled back afterwards.
 */
@SpringBootTest
@Transactional
class TutorComplaintPenaltyRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final LocalDateTime SINCE = NOW.minusDays(365);

    @Autowired TutorComplaintPenaltyRepository repository;
    @Autowired JdbcTemplate jdbc;

    private UUID subjectId;
    private UUID gradeId;
    private UUID tutorId;
    private UUID tutorUserId;
    private UUID studentId;
    private UUID studentUserId;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", subjectId, "subject-" + subjectId);
        jdbc.update("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')", gradeId, "grade-" + gradeId);
        tutorUserId = insertUser();
        tutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", tutorId, tutorUserId);
        studentUserId = insertUser();
        studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO students (id, user_id) VALUES (?, ?)", studentId, studentUserId);
        contractId = insertContract(tutorId, "ACTIVE", false);
    }

    @Test
    void findsComplaintsTheTutorWasFoundAtFaultForInEitherRole() {
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "RESPONDENT", NOW.minusDays(10), false);
        insertComplaint(studentUserId, "RESOLVED", "MISCONDUCT", "BOTH", NOW.minusDays(20), false);
        insertComplaint(tutorUserId, "RESOLVED", "SERIOUS_MISCONDUCT", "COMPLAINANT", NOW.minusDays(30), false);
        insertComplaint(tutorUserId, "RESOLVED", "OTHER", "BOTH", NOW.minusDays(40), false);
        // Concluded before categories existed.
        insertComplaint(studentUserId, "RESOLVED", null, "RESPONDENT", NOW.minusDays(50), false);

        assertThat(repository.findFaultComplaints(tutorId, SINCE)).containsExactlyInAnyOrder(
                new FaultComplaint(ComplaintCategory.ABSENCE, ComplaintFaultParty.RESPONDENT, false),
                new FaultComplaint(ComplaintCategory.MISCONDUCT, ComplaintFaultParty.BOTH, false),
                new FaultComplaint(ComplaintCategory.SERIOUS_MISCONDUCT, ComplaintFaultParty.COMPLAINANT, true),
                new FaultComplaint(ComplaintCategory.OTHER, ComplaintFaultParty.BOTH, true),
                new FaultComplaint(null, ComplaintFaultParty.RESPONDENT, false));
    }

    @Test
    void ignoresComplaintsWithoutAConfirmedTutorFault() {
        // Not concluded, or concluded without a fault: the database only allows a fault party on RESOLVED.
        for (String status : new String[] { "PENDING", "IN_REVIEW", "AWAITING_EVIDENCE", "REJECTED", "CANCELLED" }) {
            insertComplaint(studentUserId, status, "ABSENCE", null, NOW.minusDays(5), false);
        }
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "NONE", NOW.minusDays(5), false);
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", null, NOW.minusDays(5), false);
        // The other party was at fault.
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "COMPLAINANT", NOW.minusDays(5), false);
        insertComplaint(tutorUserId, "RESOLVED", "ABSENCE", "RESPONDENT", NOW.minusDays(5), false);
        // Outside the window, or deleted.
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "RESPONDENT", SINCE.minusDays(1), false);
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "RESPONDENT", NOW.minusDays(5), true);

        assertThat(repository.findFaultComplaints(tutorId, SINCE)).isEmpty();
    }

    @Test
    void ignoresComplaintsOnAnotherTutorsContracts() {
        UUID otherTutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", otherTutorId, insertUser());
        contractId = insertContract(otherTutorId, "ACTIVE", false);
        insertComplaint(studentUserId, "RESOLVED", "ABSENCE", "RESPONDENT", NOW.minusDays(5), false);

        assertThat(repository.findFaultComplaints(tutorId, SINCE)).isEmpty();
        assertThat(repository.findFaultComplaints(otherTutorId, SINCE)).hasSize(1);
    }

    @Test
    void countsSignedContractsOnly() {
        insertContract(tutorId, "COMPLETED", false);
        insertContract(tutorId, "CANCELLED", false);
        insertContract(tutorId, "PENDING", false);
        insertContract(tutorId, "COMPLETED", true);

        // The ACTIVE one from setUp, COMPLETED and CANCELLED.
        assertThat(repository.countSignedContracts(tutorId)).isEqualTo(3);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
                """, id, "cid-" + id, id + "@example.com");
        return id;
    }

    private UUID insertContract(UUID tutor, String status, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contracts
                    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period,
                     total_lession, start_date, end_date, status, created_by_user_id, preferred_schedule, deleted_at,
                     student_tutor_request_id)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 100000, 'MONTHLY', 8, DATE '2026-06-01', DATE '2026-08-01',
                        ?, ?, 'Monday evening', ?, ?)
                """, id, studentId, tutor, subjectId, gradeId, status, studentUserId, deleted ? NOW : null,
                insertApplication(tutor));
        return id;
    }

    /** A contract needs the application it came from. */
    private UUID insertApplication(UUID tutor) {
        UUID teachingRequestId = UUID.randomUUID();
        jdbc.update("INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status) VALUES (?, ?, 'ONLINE', 'OPEN')",
                teachingRequestId, tutor);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'ACCEPTED')
                """, id, studentId, gradeId, teachingRequestId);
        return id;
    }

    private void insertComplaint(UUID senderUserId, String status, String category, String faultParty,
                                 LocalDateTime resolvedAt, boolean deleted) {
        jdbc.update("""
                INSERT INTO complaints
                    (id, user_id, contract_id, title, content, status, category, fault_party, resolved_at, deleted_at)
                VALUES (?, ?, ?, 'Title', 'Content', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), senderUserId, contractId, status, category, faultParty, resolvedAt,
                deleted ? NOW : null);
    }
}
