package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.request.StudentTutorRequestCreateRequest;
import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.entity.RecommendationDecisionSnapshot;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.DecisionRequestType;
import com.ptutor.backend.entity.enums.DecisionSampleKind;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.repository.RecommendationDecisionSnapshotRepository;
import com.ptutor.backend.repository.StudentRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;

/**
 * Creating an application or a proposal records two willingness snapshots in the same transaction (design 12,
 * 12.6), computed before the application is saved. Rows are rolled back after each test.
 */
@SpringBootTest
@Transactional
class DecisionSnapshotRecorderIntegrationTest {

    /** The platform rate is not measured on this database: the default applies. */
    private static final double P0 = 0.6;

    @Autowired StudentTutorRequestService studentTutorRequestService;
    @Autowired TutorStudentRequestService tutorStudentRequestService;
    @Autowired DecisionSnapshotRecorder recorder;
    @Autowired RecommendationDecisionSnapshotRepository snapshotRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired RecommendationSettings settings;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private UUID subjectId;
    private UUID gradeId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", Integer.MAX_VALUE);
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán-" + UUID.randomUUID());
        gradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)",
                "Lớp 9-" + UUID.randomUUID());
    }

    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", 20);
    }

    @Test
    void aStudentApplicationRecordsTheTutorsDecisionAndTheStudentsInitiative() {
        UUID tutorId = tutor();
        UUID posting = insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status, expected_price,
                                               quantity)
                VALUES (?, ?, ?, 'Toán 9', 'ONLINE', 'OPEN', 200000, 2)
                """, tutorId, subjectId);
        insert("INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES (?, ?, ?)", gradeId,
                posting);
        // Another student is already waiting for this tutor.
        insert("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING')
                """, student(), gradeId, posting);
        UUID studentUserId = user();
        insert("INSERT INTO students (id, user_id) VALUES (?, ?)", studentUserId);

        UUID applicationId = studentTutorRequestService.createForStudent(studentUserId, posting,
                new StudentTutorRequestCreateRequest(gradeId, new BigDecimal("180000"), LearningMode.ONLINE, null,
                        null)).id();

        List<RecommendationDecisionSnapshot> snapshots = snapshots(DecisionRequestType.STUDENT_TUTOR_REQUEST,
                applicationId);
        assertThat(snapshots).extracting(RecommendationDecisionSnapshot::getModelKey,
                RecommendationDecisionSnapshot::getSampleKind).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WeightModel.B_S2T, DecisionSampleKind.DECISION),
                        org.assertj.core.groups.Tuple.tuple(WeightModel.B_T2S, DecisionSampleKind.INITIATIVE));

        JsonNode decision = snapshots.get(0).getFeatures();
        // A budget of 180 000 for an asking price of 200 000: 1 - 20 000 / (0.3 x 200 000).
        double price = 1 - 20_000.0 / 60_000;
        assertThat(decision.path("fitPrice").asDouble()).isCloseTo(price, within(1e-9));
        // Level and schedule unknown: they take the mean of the known parts, here the price alone.
        assertThat(decision.path("fitSegment").asDouble()).isCloseTo(price, within(1e-9));
        assertThat(decision.path("fitSched").asDouble()).isCloseTo(price, within(1e-9));
        assertThat(decision.path("hist").asDouble()).isCloseTo(P0, within(1e-9));
        // Before this application: one waiting for two places.
        assertThat(decision.path("pending").asInt()).isEqualTo(1);
        assertThat(decision.path("cap").asDouble()).isCloseTo(2 / (2 + 0.5), within(1e-9));
        assertThat(decision.path("reachedOut").asBoolean()).isFalse();

        JsonNode initiative = snapshots.get(1).getFeatures();
        assertThat(initiative.path("fitQuality").asDouble()).isCloseTo(0.5, within(1e-9));
        // No style wanted: neutral. The tutor asks 200 000 against a budget of 180 000: 1 - 20 000 / (0.5 x 180 000).
        assertThat(initiative.path("fitStyle").asDouble()).isCloseTo(0.5, within(1e-9));
        assertThat(initiative.path("fitPrice").asDouble()).isCloseTo(1 - 20_000.0 / 90_000, within(1e-9));
        assertThat(initiative.path("hist").asDouble()).isCloseTo(P0, within(1e-9));
        assertThat(initiative.path("reachedOut").asBoolean()).isTrue();
    }

    @Test
    void aTutorProposalRecordsTheStudentsDecisionAndTheTutorsInitiative() {
        UUID studentId = student();
        UUID posting = insert("""
                INSERT INTO studying_requests (id, student_id, subject_id, grade_id, learning_mode, status, max_price,
                                               title)
                VALUES (?, ?, ?, ?, 'ONLINE', 'OPEN', 150000, 'Tìm gia sư Toán')
                """, studentId, subjectId, gradeId);
        // Another tutor is already waiting for this student.
        insert("""
                INSERT INTO tutor_student_requests (id, tutor_id, grade_id, studying_request_id, teaching_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING')
                """, tutor(), gradeId, posting);
        UUID tutorUserId = user();
        insert("INSERT INTO tutors (id, user_id, recommendation_score) VALUES (?, ?, 50)", tutorUserId);

        UUID proposalId = tutorStudentRequestService.create(tutorUserId, posting,
                new TutorStudentRequestCreateRequest(gradeId, new BigDecimal("160000"), TeachingMode.ONLINE, null,
                        null)).id();

        List<RecommendationDecisionSnapshot> snapshots = snapshots(DecisionRequestType.TUTOR_STUDENT_REQUEST,
                proposalId);
        assertThat(snapshots).extracting(RecommendationDecisionSnapshot::getModelKey,
                RecommendationDecisionSnapshot::getSampleKind).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WeightModel.B_S2T, DecisionSampleKind.INITIATIVE),
                        org.assertj.core.groups.Tuple.tuple(WeightModel.B_T2S, DecisionSampleKind.DECISION));

        JsonNode decision = snapshots.get(1).getFeatures();
        assertThat(decision.path("fitQuality").asDouble()).isCloseTo(0.5, within(1e-9));
        assertThat(decision.path("fitStyle").asDouble()).isCloseTo(0.5, within(1e-9));
        // The tutor asks 160 000 against a budget of 150 000: 1 - 10 000 / (0.5 x 150 000).
        assertThat(decision.path("fitPrice").asDouble()).isCloseTo(1 - 10_000.0 / 75_000, within(1e-9));
        assertThat(decision.path("pending").asInt()).isEqualTo(1);
        assertThat(decision.path("cap").asDouble()).isCloseTo(1 / (1 + 0.5), within(1e-9));
        assertThat(decision.path("reachedOut").asBoolean()).isFalse();

        JsonNode initiative = snapshots.get(0).getFeatures();
        // The tutor's side of the proposal: asking 160 000, budget 150 000: 1 - 10 000 / (0.3 x 160 000).
        double price = 1 - 10_000.0 / 48_000;
        assertThat(initiative.path("fitPrice").asDouble()).isCloseTo(price, within(1e-9));
        assertThat(initiative.path("fitSegment").asDouble()).isCloseTo(price, within(1e-9));
        assertThat(initiative.path("hist").asDouble()).isCloseTo(P0, within(1e-9));
        assertThat(initiative.path("cap").asDouble()).isCloseTo(1.0, within(1e-9));
        assertThat(initiative.path("reachedOut").asBoolean()).isTrue();
    }

    @Test
    void aFailingSnapshotLeavesTheTransactionUsable() {
        UUID tutorId = tutor();
        UUID posting = insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status, expected_price)
                VALUES (?, ?, ?, 'Toán 9', 'ONLINE', 'OPEN', 200000)
                """, tutorId, subjectId);
        UUID studentId = student();
        entityManager.flush();
        TeachingRequest teachingRequest = entityManager.find(TeachingRequest.class, posting);
        DecisionSnapshotRecorder.Prepared prepared = recorder.prepareStudentApplication(
                studentRepository.findById(studentId).orElseThrow(), teachingRequest, null, null, LearningMode.ONLINE);
        UUID requestId = UUID.randomUUID();

        recorder.record(prepared, requestId);
        // The same snapshots again break the unique constraint: the failed insert is rolled back to its savepoint.
        recorder.record(prepared, requestId);

        // Still usable: a failed statement without a savepoint would abort every later statement.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_decision_snapshots WHERE request_id = ?",
                Long.class, requestId)).isEqualTo(2);
        insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Văn-" + UUID.randomUUID());
    }

    @Test
    void aSnapshotThatCannotBeComputedIsSkippedWithoutFailing() {
        UUID studentId = student();
        entityManager.flush();
        // A posting without a tutor: the tutor's side cannot be built.
        TeachingRequest withoutTutor = TeachingRequest.builder().build();
        withoutTutor.setId(UUID.randomUUID());

        DecisionSnapshotRecorder.Prepared prepared = recorder.prepareStudentApplication(
                studentRepository.findById(studentId).orElseThrow(), withoutTutor, null, null, LearningMode.ONLINE);

        assertThat(prepared).isNull();
        recorder.record(null, UUID.randomUUID());
        insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Sử-" + UUID.randomUUID());
    }

    private List<RecommendationDecisionSnapshot> snapshots(DecisionRequestType type, UUID requestId) {
        entityManager.flush();
        entityManager.clear();
        return snapshotRepository.findAllByRequestTypeAndRequestIdOrderByModelKey(type, requestId);
    }

    private UUID tutor() {
        return insert("INSERT INTO tutors (id, user_id, recommendation_score) VALUES (?, ?, 50)", user());
    }

    private UUID student() {
        return insert("INSERT INTO students (id, user_id) VALUES (?, ?)", user());
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status, first_name, last_name)
                VALUES (?, ?, ?, 'x', 'ACTIVE', 'An', 'Nguyễn')
                """, id, "cid-" + id, id + "@example.com");
        return id;
    }

    private UUID insert(String sql, Object... args) {
        UUID id = UUID.randomUUID();
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        jdbc.update(sql, all);
        return id;
    }
}
