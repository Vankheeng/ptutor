package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.request.StudentRecommendationRequest.Filters;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Area;
import com.ptutor.backend.dto.response.StudentRecommendationResponse;
import com.ptutor.backend.dto.response.StudentRecommendationResponse.Item;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.StudyingCandidateRepository;
import com.ptutor.backend.repository.TutorRepository;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.dto.response.RelaxationSuggestion;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "Tutor looks for students" end to end against PostgreSQL. Only the AI service is replaced: the
 * supply vector is fixed and each studying posting gets an embedding with a chosen cosine to it.
 */
@SpringBootTest
@Transactional
class StudentRecommendationIntegrationTest {

    @Autowired TutorRepository tutorRepository;
    @Autowired StudyingCandidateRepository candidateRepository;
    @Autowired RecommendationLogRepository logRepository;
    @Autowired SupplyContextBuilder supplyContextBuilder;
    @Autowired TutorSideRanker ranker;
    @Autowired RecommendationService recommendationService;
    @Autowired RecommendationLogger recommendationLogger;
    @Autowired RecommendationSettings settings;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Autowired com.ptutor.backend.repository.RecommendationRerankCacheRepository rerankCacheRepository;
    @Autowired com.ptutor.backend.repository.RecommendationEntityTagRepository entityTagRepository;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired MatchReasonBuilder matchReasonBuilder;
    @Autowired RelaxationService relaxationService;
    @Autowired com.ptutor.backend.repository.RecommendationTagRepository tagRepository;

    private QueryEmbeddingService queryEmbeddingService;
    private AiServiceClient aiServiceClient;
    private StudentRecommendationService service;

    private UUID provinceId;
    private UUID otherProvinceId;
    private UUID homeDistrictId;
    private UUID nearDistrictId;
    private UUID farDistrictId;
    private UUID subjectId;
    private UUID otherSubjectId;
    private UUID gradeId;
    private UUID secondGradeId;
    private UUID otherGradeId;
    private UUID tutorUserId;
    private UUID tutorId;
    private UUID reviewerId;

    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", 20);
        ReflectionTestUtils.setField(settings, "relaxationBudgetMs", 400L);
        ReflectionTestUtils.setField(settings, "relaxationGoodThreshold", 0.50);
    }

    @BeforeEach
    void setUp() {
        // Never measure the platform rate on whatever this database happens to contain.
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", Integer.MAX_VALUE);
        queryEmbeddingService = mock(QueryEmbeddingService.class);
        when(queryEmbeddingService.embed(any())).thenReturn(Optional.of(unit(1.0)));
        aiServiceClient = mock(AiServiceClient.class);
        RerankService rerankService = new RerankService(aiServiceClient, rerankCacheRepository,
                entityTagRepository, textBuilder, entityManager, settings);
        service = new StudentRecommendationService(tutorRepository, candidateRepository, logRepository,
                supplyContextBuilder, queryEmbeddingService, ranker, rerankService, matchReasonBuilder,
                relaxationService, tagRepository, recommendationService, recommendationLogger, settings);
        // Suggestions are computed against a real database: never let a slow machine cut them short.
        ReflectionTestUtils.setField(settings, "relaxationBudgetMs", 60_000L);

        provinceId = insert("INSERT INTO provinces (id, name) VALUES (?, 'Tỉnh A')");
        otherProvinceId = insert("INSERT INTO provinces (id, name) VALUES (?, 'Tỉnh B')");
        homeDistrictId = insert("INSERT INTO districts (id, name, province_id) VALUES (?, 'Quận Nhà', ?)", provinceId);
        nearDistrictId = insert("INSERT INTO districts (id, name, province_id) VALUES (?, 'Quận Gần', ?)", provinceId);
        farDistrictId = insert("INSERT INTO districts (id, name, province_id) VALUES (?, 'Quận Xa', ?)",
                otherProvinceId);
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán-" + UUID.randomUUID());
        otherSubjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')",
                "Văn-" + UUID.randomUUID());
        gradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)", "Lớp 9-" + UUID.randomUUID());
        secondGradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 8)",
                "Lớp 8-" + UUID.randomUUID());
        otherGradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 3)",
                "Lớp 3-" + UUID.randomUUID());
        tutorUserId = user(homeDistrictId, "ACTIVE");
        reviewerId = insert("INSERT INTO employees (id, user_id, role) VALUES (?, ?, 1)", user(null, "ACTIVE"));
        tutorId = verifiedTutor(tutorUserId, "Dạy Toán THCS", 80);
    }

    // ---------------------------------------------------------------- criteria

    @Test
    void asksForSubjectAndGradesWhenTheyAreMissing() {
        request(student(), new Request());

        StudentRecommendationResponse response = service.recommendStudents(tutorUserId, null, 0, 20, null);

        assertThat(response.needsInput()).containsExactly("subjectId", "gradeIds");
        assertThat(response.items()).isEmpty();
        assertThat(response.logId()).isNull();
    }

    @Test
    void aTutorWithoutAnyTeachingPostingCanSearch() {
        UUID posting = request(student(), new Request());

        StudentRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(posting);
        assertThat(response.queryContext().path("expectedPrice").isNull()).isTrue();
    }

    @Test
    void fillsMissingCriteriaFromTheTutorsOwnPostingAndDefaultsThePriceToTheirMedian() {
        UUID ownPosting = insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status, expected_price)
                VALUES (?, ?, ?, 'Luyện thi vào 10', 'OFFLINE', 'OPEN', 200000)
                """, tutorId, subjectId);
        insert("INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES (?, ?, ?)", gradeId,
                ownPosting);
        insert("INSERT INTO teaching_request_districts (id, teaching_request_id, district_id) VALUES (?, ?, ?)",
                ownPosting, nearDistrictId);
        insert("""
                INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status, expected_price)
                VALUES (?, ?, 'ONLINE', 'CLOSED', 100000)
                """, tutorId);
        insert("""
                INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status, expected_price)
                VALUES (?, ?, 'ONLINE', 'CLOSED', 160000)
                """, tutorId);
        UUID match = request(student(), new Request().mode("OFFLINE").district(nearDistrictId));
        request(student(), new Request().mode("OFFLINE").district(homeDistrictId));
        request(student(), new Request().mode("ONLINE"));

        StudentRecommendationResponse fromPosting = service.recommendStudents(tutorUserId,
                new StudentRecommendationRequest(null, ownPosting), 0, 20, null);
        StudentRecommendationResponse fromFilters = search(anywhere());

        assertThat(ids(fromPosting)).containsExactly(match);
        JsonNode context = fromPosting.queryContext();
        assertThat(context.path("sources").path("subject").asString()).isEqualTo("TEACHING_REQUEST");
        assertThat(context.path("sources").path("grades").asString()).isEqualTo("TEACHING_REQUEST");
        assertThat(context.path("sources").path("area").asString()).isEqualTo("TEACHING_REQUEST");
        assertThat(context.path("learningMode").asString()).isEqualTo("OFFLINE");
        assertThat(context.path("expectedPrice").decimalValue()).isEqualByComparingTo("200000");
        assertThat(fromFilters.queryContext().path("expectedPrice").decimalValue())
                .as("median of 100k, 160k, 200k").isEqualByComparingTo("160000");
        assertThat(fromFilters.queryContext().path("sources").path("expectedPrice").asString()).isEqualTo("PROFILE");
    }

    @Test
    void rejectsAnotherTutorsPostingAndUnknownGrades() {
        assertThatThrownBy(() -> service.recommendStudents(tutorUserId,
                new StudentRecommendationRequest(anywhere(), UUID.randomUUID()), 0, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("TEACHING_REQUEST_NOT_FOUND"));
        assertThatThrownBy(() -> search(new Filters(subjectId, List.of(gradeId, UUID.randomUUID()), null, null, null,
                null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_GRADE"));
    }

    // ---------------------------------------------------------------- hard filters

    @Test
    void onlyOpenPostingsOfActiveStudentsForTheChosenSubjectAndGradesAreCandidates() {
        UUID valid = request(student(), new Request());
        UUID secondGrade = request(student(), new Request().grade(secondGradeId));
        request(student(), new Request().status("DRAFT"));
        request(student(), new Request().status("MATCHED"));
        request(student("INACTIVE"), new Request());
        request(student(), new Request().subject(otherSubjectId));
        request(student(), new Request().grade(otherGradeId));
        UUID deleted = request(student(), new Request());
        jdbc.update("UPDATE studying_requests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deleted);

        assertThat(ids(search(anywhere()))).containsExactlyInAnyOrder(valid, secondGrade);
    }

    @Test
    void modeAndAreaAreDecidedByTheTutor() {
        UUID online = request(student(), new Request().mode("ONLINE"));
        UUID home = request(student(), new Request().mode("OFFLINE").district(homeDistrictId));
        UUID near = request(student(), new Request().mode("OFFLINE").district(nearDistrictId));
        UUID far = request(student(), new Request().mode("OFFLINE").district(farDistrictId));

        assertThat(ids(search(filters(ModePreference.ONLINE, null)))).containsExactly(online);
        assertThat(ids(search(filters(null, null)))).as("default area: the district the tutor lives in")
                .containsExactlyInAnyOrder(online, home);
        assertThat(ids(search(filters(ModePreference.OFFLINE, new Area(AreaScope.DISTRICTS, List.of(nearDistrictId,
                farDistrictId), null))))).containsExactlyInAnyOrder(near, far);
        assertThat(ids(search(filters(ModePreference.OFFLINE, new Area(AreaScope.PROVINCE, null, null)))))
                .containsExactlyInAnyOrder(home, near);
        assertThat(ids(search(filters(ModePreference.OFFLINE, new Area(AreaScope.ANY, null, null)))))
                .containsExactlyInAnyOrder(home, near, far);
    }

    @Test
    void budgetFarBelowTheHopedPriceIsFilteredOut() {
        UUID enough = request(student(), new Request().maxPrice("200000"));
        UUID atLimit = request(student(), new Request().maxPrice("120000"));
        request(student(), new Request().maxPrice("119000"));
        UUID noBudget = request(student(), new Request().maxPrice(null));

        StudentRecommendationResponse response = search(new Filters(subjectId, List.of(gradeId, secondGradeId), null,
                new Area(AreaScope.ANY, null, null), new BigDecimal("180000"), null, null));

        assertThat(ids(response)).containsExactlyInAnyOrder(enough, atLimit, noBudget);
        assertThat(ids(response).get(0)).isEqualTo(enough);
    }

    @Test
    void hidesStudentsAlreadyContractedOrProposedToOrWhoRecentlyRejectedThatProposal() {
        UUID contracted = student();
        request(contracted, new Request());
        UUID oldPosting = request(contracted, new Request().status("CLOSED"));
        UUID acceptedProposal = proposal(tutorId, oldPosting, "ACCEPTED", 5, null);
        UUID contractedUser = jdbc.queryForObject("SELECT user_id FROM students WHERE id = ?", UUID.class, contracted);
        jdbc.update("""
                INSERT INTO contracts
                    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period,
                     total_lession, start_date, end_date, status, created_by_user_id, preferred_schedule,
                     tutor_student_request_id)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 100000, 'MONTHLY', 8, DATE '2026-06-01', DATE '2027-06-01',
                        'ACTIVE', ?, 'Monday evening', ?)
                """, UUID.randomUUID(), contracted, tutorId, subjectId, gradeId, contractedUser, acceptedProposal);
        proposal(tutorId, request(student(), new Request()), "PENDING", 1, null);
        proposal(tutorId, request(student(), new Request()), "REJECTED", 10, null);
        UUID rejectedLongAgo = request(student(), new Request());
        proposal(tutorId, rejectedLongAgo, "REJECTED", 31, null);
        UUID proposedByAnotherTutor = request(student(), new Request());
        proposal(otherTutor(70), proposedByAnotherTutor, "PENDING", 1, null);
        UUID untouched = request(student(), new Request());

        assertThat(ids(search(anywhere())))
                .containsExactlyInAnyOrder(rejectedLongAgo, proposedByAnotherTutor, untouched);
    }

    // ---------------------------------------------------------------- scoring

    @Test
    void ranksBySuitabilityForTheTutorAndByHowLikelyTheStudentIsToAccept() {
        UUID close = request(student(), new Request().cosine(0.80));
        UUID distant = request(student(), new Request().cosine(0.40));
        UUID closeButCrowded = request(student(), new Request().cosine(0.80));
        for (int index = 0; index < 6; index++) {
            proposal(otherTutor(70), closeButCrowded, "PENDING", 1, null);
        }

        StudentRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(close, distant, closeButCrowded);
        JsonNode log = log(response.logId());
        // a' = 0.30 * 1 + 0.05 * 1 (online) + 0.65 * 0.5; b' = 0.5 * 0.6 + 0.5 * (0.4 * 0.8 + 0.15 + 0.15)
        assertThat(of(log, close).path("a").asDouble()).isCloseTo(0.675, within(0.002));
        assertThat(of(log, close).path("b").asDouble()).isCloseTo(0.61, within(0.002));
        assertThat(of(log, close).path("q").isNull()).as("students have no quality factor").isTrue();
        assertThat(response.items().get(0).score()).isCloseTo(2 * 0.675 * 0.61 / (0.675 + 0.61), within(0.002));
        assertThat(of(log, closeButCrowded).path("willingness").path("cap").asDouble()).isCloseTo(0.25, within(0.001));
    }

    @Test
    void studentWhoAlreadyAppliedToThisTutorIsCertainAndOneWhoRejectedThemIsPushedDown() {
        UUID applicant = student();
        UUID fromApplicant = request(applicant, new Request().cosine(0.60));
        UUID tutorsPosting = insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, teaching_mode, status)
                VALUES (?, ?, ?, 'ONLINE', 'OPEN')
                """, tutorId, subjectId);
        insert("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING')
                """, applicant, gradeId, tutorsPosting);
        UUID rejecting = student();
        UUID fromRejecting = request(rejecting, new Request().cosine(0.60));
        proposal(tutorId, request(rejecting, new Request().status("CLOSED")), "REJECTED", 40, null);
        UUID plain = request(student(), new Request().cosine(0.60));

        StudentRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(fromApplicant, plain, fromRejecting);
        JsonNode log = log(response.logId());
        assertThat(of(log, fromApplicant).path("b").asDouble()).isEqualTo(1.0);
        assertThat(of(log, fromApplicant).path("willingness").path("invited").asBoolean()).isTrue();
        assertThat(of(log, fromRejecting).path("g").asDouble()).isEqualTo(0.5);
    }

    @Test
    void studentsPastDecisionsAboutSimilarTutorsChangeTheirWillingness() {
        UUID welcoming = student();
        UUID picky = student();
        UUID fromWelcoming = request(welcoming, new Request().cosine(0.60));
        UUID fromPicky = request(picky, new Request().cosine(0.60));
        UUID oldWelcoming = request(welcoming, new Request().status("CLOSED"));
        UUID oldPicky = request(picky, new Request().status("CLOSED"));
        for (int index = 0; index < 4; index++) {
            // Proposals from tutors of the same quality (80) as the searching tutor.
            proposal(otherTutor(80), oldWelcoming, "ACCEPTED", 20, null);
            proposal(otherTutor(80), oldPicky, "REJECTED", 20, null);
        }

        StudentRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(fromWelcoming, fromPicky);
        JsonNode log = log(response.logId());
        // k = 0.5 * 0.5 (price unknown) + 0.5 * 1 (same quality) = 0.75 per decision
        assertThat(of(log, fromWelcoming).path("willingness").path("hist").asDouble())
                .isCloseTo((4 * 0.75 + 1.2) / (4 * 0.75 + 2), within(0.001));
        assertThat(of(log, fromPicky).path("willingness").path("hist").asDouble())
                .isCloseTo(1.2 / (4 * 0.75 + 2), within(0.001));
    }

    // ---------------------------------------------------------------- privacy, log and paging

    @Test
    void returnsOnlyLimitedInformationAboutTheStudent() throws Exception {
        UUID studentUser = user(nearDistrictId, "ACTIVE");
        jdbc.update("""
                UPDATE users SET first_name = 'Bí', last_name = 'Mật', phone = '0912345678',
                       detail_address = '12 Ngõ Kín', avatar_url = 'https://cdn.example.com/kin.png'
                WHERE id = ?
                """, studentUser);
        UUID student = insert("INSERT INTO students (id, user_id) VALUES (?, ?)", studentUser);
        UUID posting = request(student, new Request().mode("OFFLINE").district(nearDistrictId)
                .minPrice("150000").maxPrice("200000"));
        jdbc.update("""
                UPDATE studying_requests SET title = 'Tìm gia sư Toán 9', learning_goals = 'Thi vào 10',
                       detail_address = '12 Ngõ Kín' WHERE id = ?
                """, posting);

        StudentRecommendationResponse response = search(filters(null, new Area(AreaScope.ANY, null, null)));

        Item item = response.items().get(0);
        assertThat(item.studyingRequestId()).isEqualTo(posting);
        assertThat(item.student().displayName()).startsWith("Học sinh Lớp 9");
        assertThat(item.student().district()).isEqualTo("Quận Gần");
        assertThat(item.title()).isEqualTo("Tìm gia sư Toán 9");
        assertThat(item.learningGoals()).isEqualTo("Thi vào 10");
        assertThat(item.priceRange().min()).isEqualByComparingTo("150000");
        assertThat(item.priceRange().max()).isEqualByComparingTo("200000");
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("Bí").doesNotContain("Mật").doesNotContain("0912345678")
                .doesNotContain("Ngõ Kín").doesNotContain("kin.png").doesNotContain(studentUser.toString())
                .doesNotContain(student.toString());
    }

    @Test
    void writesALogForTheTutorAndServesLaterPagesFromIt() {
        UUID first = request(student(), new Request().cosine(0.80));
        UUID second = request(student(), new Request().cosine(0.70));
        UUID third = request(student(), new Request().cosine(0.60));
        long logsBefore = logRepository.count();

        StudentRecommendationResponse pageOne = service.recommendStudents(tutorUserId,
                new StudentRecommendationRequest(anywhere(), null), 0, 2, null);
        entityManager.flush();
        jdbc.update("UPDATE studying_requests SET status = 'CLOSED' WHERE id = ?", first);
        entityManager.clear();
        when(queryEmbeddingService.embed(any())).thenThrow(new AssertionError("a later page must not embed again"));
        StudentRecommendationResponse pageTwo = service.recommendStudents(tutorUserId, null, 1, 2, pageOne.logId());

        assertThat(ids(pageOne)).containsExactly(first, second);
        assertThat(ids(pageTwo)).containsExactly(third);
        assertThat(pageTwo.items().get(0).rank()).isEqualTo(3);
        assertThat(pageTwo.totalElements()).isEqualTo(3);
        assertThat(pageTwo.queryContext().has("supplyText")).isFalse();
        assertThat(logRepository.count()).isEqualTo(logsBefore + 1);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT searcher_tutor_id, searcher_student_id, direction, algorithm_version,
                       query_context::text AS context
                FROM recommendation_logs WHERE id = ?
                """, pageOne.logId());
        assertThat(row.get("searcher_tutor_id")).isEqualTo(tutorId);
        assertThat(row.get("searcher_student_id")).isNull();
        assertThat(row.get("direction")).isEqualTo("TUTOR_TO_STUDENT");
        assertThat((String) row.get("context")).contains("supplyText").contains("Dạy Toán THCS");
    }

    @Test
    void anotherTutorOrAStudentSearchLogCannotBeRead() {
        request(student(), new Request());
        UUID logId = search(anywhere()).logId();
        UUID otherUser = user(homeDistrictId, "ACTIVE");
        verifiedTutor(otherUser, null, 70);

        assertThatThrownBy(() -> service.recommendStudents(otherUser, null, 0, 20, logId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("RECOMMENDATION_LOG_NOT_FOUND");
                });
    }

    @Test
    void tutorWhoseProfileIsNotVerifiedCannotLookForStudents() {
        request(student(), new Request());
        UUID logId = search(anywhere()).logId();
        jdbc.update("""
                UPDATE tutors SET profile_status = 'PENDING', profile_reviewed_by = NULL, profile_reviewed_at = NULL
                WHERE id = ?
                """, tutorId);
        entityManager.clear();

        assertThatThrownBy(() -> search(anywhere()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("TUTOR_PROFILE_NOT_VERIFIED");
                });
        assertThatThrownBy(() -> service.recommendStudents(tutorUserId, null, 0, 20, logId))
                .as("also when paging an earlier search")
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("TUTOR_PROFILE_NOT_VERIFIED"));
    }

    // ---------------------------------------------------------------- helpers

    // ---- one result per student, reasons, suggestions

    @Test
    void showsAStudentOnceWithTheirBestPostingAndSaysWhyItFits() {
        UUID student = student();
        UUID best = request(student, new Request().maxPrice("200000").cosine(0.80));
        request(student, new Request().maxPrice("200000").cosine(0.50));
        UUID other = request(student(), new Request().maxPrice("150000").cosine(0.60));

        StudentRecommendationResponse response = search(new Filters(subjectId, List.of(gradeId, secondGradeId),
                null, new Area(AreaScope.ANY, null, null), new BigDecimal("180000"), null, null));

        assertThat(ids(response)).containsExactly(best, other);
        Item first = response.items().get(0);
        assertThat(first.reasons()).contains("Nhu cầu của học sinh rất sát với những gì bạn dạy",
                "Ngân sách tới 200.000đ — đủ mức bạn mong muốn");
        assertThat(first.caution()).isNull();
        assertThat(response.items().get(1).caution()).isEqualTo("Ngân sách thấp hơn mức bạn mong muốn 30.000đ");

        StudentRecommendationResponse again = service.recommendStudents(tutorUserId, null, 0, 20, response.logId());
        assertThat(again.items().get(0).reasons()).isEqualTo(first.reasons());
        assertThat(again.items().get(1).caution()).isEqualTo(response.items().get(1).caution());
    }

    @Test
    void suggestsWideningTheAreaWhenFewStudentsAreFound() {
        ReflectionTestUtils.setField(settings, "relaxationGoodThreshold", 0.30);
        UUID home = request(student(), new Request().mode("OFFLINE").district(homeDistrictId).cosine(0.80));
        request(student(), new Request().mode("OFFLINE").district(nearDistrictId).cosine(0.80));
        request(student(), new Request().mode("OFFLINE").district(nearDistrictId).cosine(0.80));
        request(student(), new Request().mode("OFFLINE").district(farDistrictId).cosine(0.80));

        StudentRecommendationResponse response = search(filters(ModePreference.OFFLINE,
                new Area(AreaScope.DISTRICTS, List.of(homeDistrictId), null)));

        assertThat(ids(response)).containsExactly(home);
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::code)
                .containsExactly("AREA_ANY", "AREA_PROVINCE");
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::message).containsExactly(
                "Bỏ giới hạn khu vực: thêm 3 học viên phù hợp",
                "Mở rộng ra toàn Tỉnh A: thêm 2 học viên phù hợp");
        assertThat(response.relaxations().get(1).apply().path("area").path("provinceId").asString())
                .isEqualTo(provinceId.toString());
        assertThat(service.recommendStudents(tutorUserId, null, 0, 20, response.logId()).relaxations())
                .extracting(RelaxationSuggestion::code).containsExactly("AREA_ANY", "AREA_PROVINCE");
    }

    private UUID verifiedTutor(UUID userId, String introduction, int score) {
        return insert("""
                INSERT INTO tutors (id, user_id, introduction, recommendation_score, profile_status,
                                    profile_reviewed_by, profile_reviewed_at)
                VALUES (?, ?, ?, ?, 'VERIFIED', ?, ?)
                """, userId, introduction, new BigDecimal(score), reviewerId,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private StudentRecommendationResponse search(Filters filters) {
        return service.recommendStudents(tutorUserId, new StudentRecommendationRequest(filters, null), 0, 20, null);
    }

    private List<UUID> ids(StudentRecommendationResponse response) {
        return response.items().stream().map(Item::studyingRequestId).toList();
    }

    private Filters anywhere() {
        return filters(null, new Area(AreaScope.ANY, null, null));
    }

    private Filters filters(ModePreference mode, Area area) {
        return new Filters(subjectId, List.of(gradeId, secondGradeId), mode, area, null, null, null);
    }

    private JsonNode log(UUID logId) {
        entityManager.flush();
        entityManager.clear();
        return logRepository.findById(logId).orElseThrow().getReturnedCandidateIds();
    }

    private JsonNode of(JsonNode candidates, UUID candidateId) {
        for (JsonNode node : candidates) {
            if (node.path("candidateId").asString().equals(candidateId.toString())) {
                return node;
            }
        }
        throw new AssertionError("Candidate " + candidateId + " is not in the log");
    }

    private UUID user(UUID districtId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status, first_name, last_name, district_id)
                VALUES (?, ?, ?, 'x', ?, 'An', 'Nguyễn', ?)
                """, id, "cid-" + id, id + "@example.com", status, districtId);
        return id;
    }

    private UUID student() {
        return student("ACTIVE");
    }

    private UUID student(String accountStatus) {
        return insert("INSERT INTO students (id, user_id) VALUES (?, ?)", user(null, accountStatus));
    }

    private UUID otherTutor(int score) {
        return insert("INSERT INTO tutors (id, user_id, recommendation_score) VALUES (?, ?, ?)",
                user(null, "ACTIVE"), new BigDecimal(score));
    }

    private UUID request(UUID studentId, Request request) {
        UUID id = insert("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, learning_mode, status, quantity, min_price, max_price,
                     district_id, title)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 'Tìm gia sư')
                """, studentId, request.subjectId == null ? subjectId : request.subjectId,
                request.gradeId == null ? gradeId : request.gradeId, request.mode, request.status, request.minPrice,
                request.maxPrice, request.districtId);
        if (request.cosine != null) {
            jdbc.update("UPDATE studying_requests SET embedding = CAST(? AS vector) WHERE id = ?",
                    literal(unit(request.cosine)), id);
        }
        return id;
    }

    /** A proposal of a tutor to a studying posting, decided (or sent) {@code daysAgo} days ago. */
    private UUID proposal(UUID proposingTutor, UUID studyingRequestId, String status, int daysAgo, String price) {
        Timestamp when = Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo));
        return insert("""
                INSERT INTO tutor_student_requests
                    (id, tutor_id, grade_id, studying_request_id, teaching_mode, status, created_at, updated_at,
                     responded_at, proposed_price)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?, ?)
                """, proposingTutor, gradeId, studyingRequestId, status, when, when,
                status.equals("PENDING") ? null : when, price == null ? null : new BigDecimal(price));
    }

    private UUID insert(String sql, Object... args) {
        UUID id = UUID.randomUUID();
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        jdbc.update(sql, all);
        return id;
    }

    private static float[] unit(double cosine) {
        float[] vector = new float[1024];
        vector[0] = (float) cosine;
        vector[1] = (float) Math.sqrt(1 - cosine * cosine);
        return vector;
    }

    private static String literal(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            text.append(index == 0 ? "" : ",").append(vector[index]);
        }
        return text.append(']').toString();
    }

    private final class Request {
        private UUID subjectId;
        private UUID gradeId;
        private String mode = "ONLINE";
        private String status = "OPEN";
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private UUID districtId;
        private Double cosine = 0.6;

        Request subject(UUID value) {
            subjectId = value;
            return this;
        }

        Request grade(UUID value) {
            gradeId = value;
            return this;
        }

        Request mode(String value) {
            mode = value;
            return this;
        }

        Request status(String value) {
            status = value;
            return this;
        }

        Request minPrice(String value) {
            minPrice = value == null ? null : new BigDecimal(value);
            return this;
        }

        Request maxPrice(String value) {
            maxPrice = value == null ? null : new BigDecimal(value);
            return this;
        }

        Request district(UUID value) {
            districtId = value;
            return this;
        }

        Request cosine(Double value) {
            cosine = value;
            return this;
        }
    }
}
