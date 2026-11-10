package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.DecisionSampleKind;
import com.ptutor.backend.repository.LearningDataRepository.CandidateFeedback;
import com.ptutor.backend.repository.LearningDataRepository.DecisionSample;
import com.ptutor.backend.repository.LearningDataRepository.SearchFeedback;

/**
 * Builds L1's training data from search logs, click logs, requests and snapshots (design 12, 12.4 to 12.6). All
 * rows are dated in January 2020, away from anything else in the database, and rolled back.
 */
@SpringBootTest
@Transactional
class LearningDataRepositoryIntegrationTest {

    private static final LocalDateTime SEARCHED = LocalDateTime.of(2020, 1, 10, 9, 0);
    private static final LocalDateTime FROM = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2020, 1, 31, 0, 0);

    @Autowired LearningDataRepository repository;
    @Autowired JdbcTemplate jdbc;

    private UUID subjectId;
    private UUID gradeId;
    private UUID studentId;
    private UUID tutorId;

    @BeforeEach
    void setUp() {
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán-" + UUID.randomUUID());
        gradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)",
                "Lớp 9-" + UUID.randomUUID());
        studentId = insert("INSERT INTO students (id, user_id) VALUES (?, ?)", user());
        tutorId = insert("INSERT INTO tutors (id, user_id) VALUES (?, ?)", user());
    }

    // ---------------------------------------------------------------- suitability side

    @Test
    void rewardsTheStrongestThingTheSearcherDidAndKeepsTheCandidatesTheyProbablyRead() {
        List<UUID> postings = List.of(posting(), posting(), posting(), posting(), posting(), posting(), posting());
        UUID log = searchLog(SEARCHED, postings, 3);
        click(log, postings.get(0), 1, "VIEWED", SEARCHED.plusMinutes(1));
        click(log, postings.get(0), 1, "FAVORITED", SEARCHED.plusMinutes(2));
        click(log, postings.get(1), 2, "DISMISSED", SEARCHED.plusMinutes(3));
        // Applied two days later without opening it from the list: still a reward of this search.
        application(postings.get(3), SEARCHED.plusDays(2));
        // Opened after the reward window: does not count.
        click(log, postings.get(2), 3, "VIEWED", SEARCHED.plusDays(8));

        List<SearchFeedback> feedback = repository.searchFeedback(WeightModel.A_S2T, FROM, TO);

        assertThat(feedback).hasSize(1);
        Map<UUID, CandidateFeedback> byId = feedback.get(0).candidates().stream()
                .collect(Collectors.toMap(CandidateFeedback::candidateId, Function.identity()));
        // Deepest interaction at rank 4, plus a margin of 2: ranks 1 to 6.
        assertThat(feedback.get(0).candidates()).extracting(CandidateFeedback::rank).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(byId.get(postings.get(0)).reward()).isEqualTo(0.6);
        assertThat(byId.get(postings.get(1)).reward()).isEqualTo(-0.2);
        assertThat(byId.get(postings.get(2)).reward()).isEqualTo(0.0);
        assertThat(byId.get(postings.get(3)).reward()).isEqualTo(1.0);
        // The candidate in an exploration slot counts twice.
        assertThat(byId.get(postings.get(2)).weight()).isEqualTo(2.0);
        assertThat(byId.get(postings.get(0)).weight()).isEqualTo(1.0);
        // Features in the order of A_S2T: sem, need, price, style, sched, area, segment; missing = neutral 0.5.
        assertThat(byId.get(postings.get(0)).features()).containsExactly(0.9, 0.5, 1.0, 0.5, 0.5, 1.0, 0.5);
    }

    @Test
    void aSearchWithoutAnyInteractionOrOutsideThePeriodGivesNothing() {
        List<UUID> postings = List.of(posting(), posting());
        searchLog(SEARCHED, postings, 0);
        UUID late = searchLog(TO.plusDays(1), postings, 0);
        click(late, postings.get(0), 1, "VIEWED", TO.plusDays(1).plusMinutes(1));

        assertThat(repository.searchFeedback(WeightModel.A_S2T, FROM, TO)).isEmpty();
    }

    // ---------------------------------------------------------------- willingness side

    @Test
    void labelsTheSnapshotsWithTheOtherSidesAnswer() {
        UUID accepted = studentRequest("ACCEPTED", SEARCHED, SEARCHED.plusHours(5));
        UUID slow = studentRequest("ACCEPTED", SEARCHED, SEARCHED.plusDays(3));
        UUID rejected = studentRequest("REJECTED", SEARCHED, SEARCHED.plusDays(1));
        UUID waiting = studentRequest("PENDING", SEARCHED, null);
        UUID stillYoung = studentRequest("PENDING", TO.minusDays(2), null);
        UUID withdrawn = studentRequest("CANCELLED", SEARCHED, SEARCHED.plusDays(1));
        for (UUID request : List.of(accepted, slow, rejected, waiting, stillYoung, withdrawn)) {
            snapshot("B_S2T", "DECISION", "STUDENT_TUTOR_REQUEST", request, SEARCHED);
        }
        // The tutor reached out: a positive sample of the tutor's willingness.
        UUID proposal = insert("""
                INSERT INTO tutor_student_requests (id, tutor_id, grade_id, studying_request_id, teaching_mode, status,
                                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING', ?, ?)
                """, tutorId, gradeId, studyingPosting("OPEN"), ts(SEARCHED), ts(SEARCHED));
        snapshot("B_S2T", "INITIATIVE", "TUTOR_STUDENT_REQUEST", proposal, SEARCHED.plusHours(1));

        Map<UUID, DecisionSample> byId = repository.decisionSamples(WeightModel.B_S2T, FROM, TO).stream()
                .collect(Collectors.toMap(DecisionSample::requestId, Function.identity()));

        assertThat(byId.keySet()).containsExactlyInAnyOrder(accepted, slow, rejected, waiting, proposal);
        assertThat(byId.get(accepted).label()).isEqualTo(1);
        assertThat(byId.get(accepted).weight()).isEqualTo(1.5);
        assertThat(byId.get(slow).weight()).isEqualTo(1.0);
        assertThat(byId.get(rejected).label()).isEqualTo(0);
        // Waited 14 days: an implicit rejection, known from then on, counting half.
        assertThat(byId.get(waiting).label()).isEqualTo(0);
        assertThat(byId.get(waiting).weight()).isEqualTo(0.5);
        assertThat(byId.get(waiting).at()).isEqualTo(SEARCHED.plusDays(14));
        assertThat(byId.get(proposal).kind()).isEqualTo(DecisionSampleKind.INITIATIVE);
        assertThat(byId.get(proposal).label()).isEqualTo(1);
        assertThat(byId.get(proposal).weight()).isEqualTo(0.5);
        assertThat(byId.get(accepted).features()).containsExactly(new double[] { 0.7, 0.4, 0.5, 1.0 }, within(1e-12));
    }

    @Test
    void aRequestCancelledBecauseThePostingWentToSomeoneElseIsAnImplicitRejection() {
        UUID posting = studyingPosting("MATCHED");
        UUID cancelled = insert("""
                INSERT INTO tutor_student_requests (id, tutor_id, grade_id, studying_request_id, teaching_mode, status,
                                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ONLINE', 'CANCELLED', ?, ?)
                """, tutorId, gradeId, posting, ts(SEARCHED), ts(SEARCHED.plusDays(2)));
        snapshot("B_T2S", "DECISION", "TUTOR_STUDENT_REQUEST", cancelled, SEARCHED);

        List<DecisionSample> samples = repository.decisionSamples(WeightModel.B_T2S, FROM, TO);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.label()).isEqualTo(0);
            assertThat(sample.weight()).isEqualTo(0.5);
            assertThat(sample.at()).isEqualTo(SEARCHED.plusDays(2));
        });
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A student's search showing {@code postings} in this order; the one at {@code explorationRank} (0 = none)
     * came from an exploration slot. Only the first posting has features; the others have none.
     */
    private UUID searchLog(LocalDateTime at, List<UUID> postings, int explorationRank) {
        StringBuilder candidates = new StringBuilder("[");
        for (int i = 0; i < postings.size(); i++) {
            candidates.append(i == 0 ? "" : ",").append("{\"candidateId\":\"").append(postings.get(i))
                    .append("\",\"rank\":").append(i + 1).append(",\"isExploration\":").append(i + 1 == explorationRank)
                    .append(",\"features\":")
                    .append(i == 0 ? "{\"sem\":0.9,\"need\":null,\"price\":1.0,\"area\":1.0}" : "{}").append('}');
        }
        candidates.append(']');
        return insert("""
                INSERT INTO recommendation_logs (id, searcher_student_id, direction, returned_candidate_ids, created_at,
                                                 updated_at)
                VALUES (?, ?, 'STUDENT_TO_TUTOR', CAST(? AS jsonb), ?, ?)
                """, studentId, candidates.toString(), ts(at), ts(at));
    }

    private void click(UUID log, UUID posting, int rank, String action, LocalDateTime at) {
        insert("""
                INSERT INTO recommendation_click_logs (id, recommendation_log_id, clicked_student_id, rank, action,
                                                       candidate_teaching_request_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, log, studentId, rank, action, posting, ts(at), ts(at));
    }

    private void application(UUID posting, LocalDateTime at) {
        insert("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status,
                                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING', ?, ?)
                """, studentId, gradeId, posting, ts(at), ts(at));
    }

    private UUID studentRequest(String status, LocalDateTime createdAt, LocalDateTime respondedAt) {
        return insert("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status,
                                                    created_at, updated_at, responded_at)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?)
                """, studentId, gradeId, posting(), status, ts(createdAt),
                ts(respondedAt == null ? createdAt : respondedAt), respondedAt == null ? null : ts(respondedAt));
    }

    private void snapshot(String model, String kind, String requestType, UUID requestId, LocalDateTime at) {
        String features = model.equals("B_S2T")
                ? "{\"hist\":0.7,\"fitSegment\":0.4,\"fitSched\":0.5,\"fitPrice\":1.0,\"cap\":1,\"pending\":0}"
                : "{\"hist\":0.6,\"fitQuality\":0.5,\"fitStyle\":0.5,\"fitPrice\":0.8,\"cap\":1,\"pending\":0}";
        jdbc.update("""
                INSERT INTO recommendation_decision_snapshots
                    (model_key, sample_kind, request_type, request_id, features, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, model, kind, requestType, requestId, features, ts(at));
    }

    private UUID posting() {
        return insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, teaching_mode, status)
                VALUES (?, ?, ?, 'ONLINE', 'OPEN')
                """, tutorId, subjectId);
    }

    private UUID studyingPosting(String status) {
        return insert("""
                INSERT INTO studying_requests (id, student_id, subject_id, grade_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', ?)
                """, studentId, subjectId, gradeId, status);
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, citizen_id, email, password, status) VALUES (?, ?, ?, 'x', 'ACTIVE')",
                id, "cid-" + id, id + "@example.com");
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

    private static Timestamp ts(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
