package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.dto.request.SavedSearchRequest;
import com.ptutor.backend.dto.request.SavedSearchUpdateRequest;
import com.ptutor.backend.dto.response.SavedSearchResponse;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.entity.enums.RerankMode;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Area;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Availability;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Filters;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Price;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.dto.response.TutorRecommendationResponse;
import com.ptutor.backend.dto.response.TutorRecommendationResponse.Item;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CandidateRepository;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.StudentRepository;

import jakarta.persistence.EntityManager;

/**
 * "Student looks for tutors" end to end against PostgreSQL: criteria merging, hard filters,
 * vector similarity, scoring, logging and paging. Only the AI service is replaced: the query
 * vector is fixed, and each posting gets an embedding with a chosen cosine to it.
 */
@SpringBootTest
@Transactional
class TutorRecommendationIntegrationTest {

    @Autowired StudentRepository studentRepository;
    @Autowired CandidateRepository candidateRepository;
    @Autowired RecommendationLogRepository logRepository;
    @Autowired QueryContextBuilder queryContextBuilder;
    @Autowired Ranker ranker;
    @Autowired RecommendationLogger recommendationLogger;
    @Autowired RecommendationSettings settings;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Autowired com.ptutor.backend.repository.RecommendationRerankCacheRepository rerankCacheRepository;
    @Autowired com.ptutor.backend.repository.RecommendationEntityTagRepository entityTagRepository;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired MatchReasonBuilder matchReasonBuilder;
    @Autowired SavedSearchService savedSearchService;
    @Autowired QueryParseService queryParseService;
    @Autowired com.ptutor.backend.repository.RecommendationMatchAlertRepository alertRepository;
    @Autowired com.ptutor.backend.repository.NotificationRepository notificationRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private NotificationService notificationService;
    private MatchAlertService matchAlertService;
    @Autowired ExplorationService explorationService;
    @Autowired PreferenceProfileService preferenceProfileService;
    @Autowired RecommendationEventService eventService;
    @Autowired com.ptutor.backend.repository.RecommendationClickLogRepository clickLogRepository;
    @Autowired com.ptutor.backend.repository.PreferenceSignalRepository signalRepository;
    @Autowired tools.jackson.databind.ObjectMapper jsonMapper;
    @Autowired StudentRecommendationService studentRecommendationService;
    @Autowired com.ptutor.backend.scheduler.ExposureStatsJob exposureStatsJob;

    private RefineService refineService;
    @Autowired RelaxationService relaxationService;
    @Autowired com.ptutor.backend.repository.RecommendationTagRepository tagRepository;

    private QueryEmbeddingService queryEmbeddingService;
    private AiServiceClient aiServiceClient;
    private RerankQueue rerankQueue;
    private final List<Runnable> backgroundWork = new java.util.ArrayList<>();
    private RecommendationService service;

    private UUID provinceId;
    private UUID otherProvinceId;
    private UUID homeDistrictId;
    private UUID nearDistrictId;
    private UUID farDistrictId;
    private UUID subjectId;
    private UUID otherSubjectId;
    private UUID gradeId;
    private UUID otherGradeId;
    private UUID reviewerId;
    private UUID studentUserId;
    private UUID studentId;

    /**
     * The settings bean is shared by the whole test context, so every change is undone afterwards. Most
     * tests check the student's side only (V2-a); the reciprocal tests switch it back on.
     */
    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", 20);
        ReflectionTestUtils.setField(settings, "rerankEnabled", true);
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.OFF);
        ReflectionTestUtils.setField(settings, "rerankWorkerQuietMs", 2000L);
        ReflectionTestUtils.setField(settings, "relaxationBudgetMs", 400L);
        ReflectionTestUtils.setField(settings, "relaxationEnabled", true);
        ReflectionTestUtils.setField(settings, "explorationEnabled", true);
        ReflectionTestUtils.setField(settings, "alertCooldownHours", 24);
        ReflectionTestUtils.setField(settings, "alertTopRank", 3);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", false);
        // Never measure the platform rate on whatever this database happens to contain.
        ReflectionTestUtils.setField(settings, "willingnessMinDecisions", Integer.MAX_VALUE);
        queryEmbeddingService = mock(QueryEmbeddingService.class);
        when(queryEmbeddingService.embed(any())).thenReturn(Optional.of(unit(1.0)));
        when(queryEmbeddingService.forSearch(any(), any())).thenCallRealMethod();
        // Without an answer from the AI service nothing is reranked: the order of phase 3 is final.
        aiServiceClient = mock(AiServiceClient.class);
        // The rerank tests check the ONLINE path; the ASYNC one switches the mode itself. Background work waits in
        // a list until a test runs it, on the test's thread and inside its transaction.
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ONLINE);
        rerankQueue = new RerankQueue(aiServiceClient, rerankCacheRepository,
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), settings,
                backgroundWork::add, System::nanoTime, Thread::sleep);
        // The worker gives way to searches for 2 s; in a test it may run right after them.
        ReflectionTestUtils.setField(settings, "rerankWorkerQuietMs", 0L);
        RerankService rerankService = new RerankService(aiServiceClient, rerankQueue, rerankCacheRepository,
                entityTagRepository, textBuilder, entityManager, settings);
        service = new RecommendationService(studentRepository, candidateRepository, logRepository,
                queryContextBuilder, queryEmbeddingService, ranker, rerankService, matchReasonBuilder,
                relaxationService, explorationService, preferenceProfileService, savedSearchService, queryParseService,
                tagRepository, recommendationLogger, settings, null);
        refineService = new RefineService(eventService, clickLogRepository, logRepository, signalRepository,
                queryEmbeddingService, service, studentRecommendationService, settings, jsonMapper);
        // Notifications are written in a transaction of their own, which cannot see this test's rows.
        notificationService = mock(NotificationService.class);
        matchAlertService = new MatchAlertService(jdbc, service, studentRecommendationService, savedSearchService,
                alertRepository, notificationService, notificationRepository, entityManager, settings,
                transactionManager);
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
        gradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)",
                "Lớp 9-" + UUID.randomUUID());
        otherGradeId = insert("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')",
                "Lớp 6-" + UUID.randomUUID());
        reviewerId = insert("INSERT INTO employees (id, user_id, role) VALUES (?, ?, 1)", user(null));
        studentUserId = user(homeDistrictId);
        studentId = insert("INSERT INTO students (id, user_id, weak_points) VALUES (?, ?, 'Hình học')", studentUserId);
    }

    // ---------------------------------------------------------------- criteria

    @Test
    void asksForSubjectAndGradeInsteadOfSearchingWhenTheyAreMissing() {
        posting(tutor(), new Posting());

        TutorRecommendationResponse response = service.recommendTutors(studentUserId, null, 0, 20, null);

        assertThat(response.needsInput()).containsExactly("subjectId", "gradeId");
        assertThat(response.items()).isEmpty();
        assertThat(response.logId()).isNull();
        assertThat(logRepository.count()).isEqualTo(countLogsBefore());
    }

    @Test
    void fillsMissingCriteriaFromTheStudentsOwnPostingThenFromTheProfile() {
        UUID ownRequest = insert("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, learning_mode, status, max_price, district_id)
                VALUES (?, ?, ?, ?, 'OFFLINE', 'OPEN', 150000, ?)
                """, studentId, subjectId, gradeId, nearDistrictId);
        UUID match = posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId).price("140000"));
        posting(tutor(), new Posting().mode("OFFLINE").districts(homeDistrictId).price("140000"));

        TutorRecommendationResponse response = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(null, null, null, ownRequest), 0, 20, null);

        assertThat(response.needsInput()).isEmpty();
        assertThat(response.items()).extracting(Item::teachingRequestId).containsExactly(match);
        assertThat(response.queryContext().path("sources").path("subject").asString()).isEqualTo("STUDYING_REQUEST");
        assertThat(response.queryContext().path("sources").path("area").asString()).isEqualTo("STUDYING_REQUEST");
        assertThat(response.queryContext().path("price").path("max").decimalValue()).isEqualByComparingTo("150000");
        assertThat(response.queryContext().path("area").path("districtIds").get(0).asString())
                .isEqualTo(nearDistrictId.toString());
    }

    @Test
    void defaultsTheAreaToTheDistrictWhereTheStudentLives() {
        UUID atHome = posting(tutor(), new Posting().mode("OFFLINE").districts(homeDistrictId));
        posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId));
        UUID online = posting(tutor(), new Posting().mode("ONLINE"));

        TutorRecommendationResponse response = search(filters());

        assertThat(response.items()).extracting(Item::teachingRequestId).containsExactlyInAnyOrder(atHome, online);
        assertThat(response.queryContext().path("sources").path("area").asString()).isEqualTo("PROFILE");
        assertThat(response.queryContext().path("sources").path("learningMode").asString()).isEqualTo("DEFAULT");
    }

    @Test
    void rejectsUnknownSubjectAndAnotherStudentsPosting() {
        Filters unknownSubject = new Filters(UUID.randomUUID(), gradeId, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> search(unknownSubject))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_SUBJECT"));
        assertThatThrownBy(() -> service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(filters(), null, null, UUID.randomUUID()), 0, 20, null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_FOUND");
                });
    }

    // ---------------------------------------------------------------- hard filters

    @Test
    void onlyOpenPostingsOfVerifiedActiveTutorsForTheChosenSubjectAndGradeAreCandidates() {
        UUID valid = posting(tutor(), new Posting());
        posting(tutor(), new Posting().status("DRAFT"));
        posting(tutor(), new Posting().status("CLOSED"));
        posting(tutor("PENDING", "ACTIVE", "70"), new Posting());
        posting(tutor("VERIFIED", "INACTIVE", "70"), new Posting());
        posting(tutor(), new Posting().subject(otherSubjectId));
        posting(tutor(), new Posting().grades(otherGradeId));
        UUID deleted = posting(tutor(), new Posting());
        jdbc.update("UPDATE teaching_requests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deleted);

        assertThat(search(anywhere()).items()).extracting(Item::teachingRequestId).containsExactly(valid);
    }

    @Test
    void learningModeFilterKeepsOnlyMatchingPostingsUnlessAny() {
        UUID online = posting(tutor(), new Posting().mode("ONLINE"));
        UUID offline = posting(tutor(), new Posting().mode("OFFLINE").districts(homeDistrictId));

        assertThat(ids(search(mode(ModePreference.ONLINE)))).containsExactly(online);
        assertThat(ids(search(mode(ModePreference.OFFLINE)))).containsExactly(offline);
        assertThat(ids(search(mode(ModePreference.ANY)))).containsExactlyInAnyOrder(online, offline);
    }

    @Test
    void areaScopeIsDecidedByTheSearcherNotByWhereTheyLive() {
        UUID home = posting(tutor(), new Posting().mode("OFFLINE").districts(homeDistrictId));
        UUID near = posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId));
        UUID far = posting(tutor(), new Posting().mode("OFFLINE").districts(farDistrictId));

        assertThat(ids(search(area(AreaScope.DISTRICTS, List.of(nearDistrictId, farDistrictId), null))))
                .containsExactlyInAnyOrder(near, far);
        assertThat(ids(search(area(AreaScope.PROVINCE, null, null)))).as("defaults to the home province")
                .containsExactlyInAnyOrder(home, near);
        assertThat(ids(search(area(AreaScope.PROVINCE, null, otherProvinceId)))).containsExactly(far);
        assertThat(ids(search(area(AreaScope.ANY, null, null)))).containsExactlyInAnyOrder(home, near, far);
    }

    @Test
    void priceAboveOneAndAHalfTimesTheBudgetIsFilteredOutAndBelowThatIsScored() {
        UUID within = posting(tutor(), new Posting().price("200000"));
        UUID slightlyOver = posting(tutor(), new Posting().price("250000"));
        UUID atLimit = posting(tutor(), new Posting().price("300000"));
        posting(tutor(), new Posting().price("300001"));
        UUID noPrice = posting(tutor(), new Posting().price(null));

        TutorRecommendationResponse response = search(new Filters(subjectId, gradeId, null,
                new Area(AreaScope.ANY, null, null), new Price(null, new BigDecimal("200000")), null, null, null, null));

        assertThat(ids(response)).containsExactlyInAnyOrder(within, slightlyOver, atLimit, noPrice);
        assertThat(ids(response).get(0)).isEqualTo(within);
        assertThat(ids(response).indexOf(slightlyOver)).isLessThan(ids(response).indexOf(atLimit));
    }

    @Test
    void hidesTutorsAlreadyContractedOrAppliedToOrWhoRecentlyRejectedThatPosting() {
        UUID contractedTutor = tutor();
        posting(contractedTutor, new Posting());
        UUID contractRequest = application(posting(contractedTutor, new Posting().status("CLOSED")), "ACCEPTED", 5);
        jdbc.update("""
                INSERT INTO contracts
                    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price, payment_period,
                     total_lession, start_date, end_date, status, created_by_user_id, preferred_schedule,
                     student_tutor_request_id)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 100000, 'MONTHLY', 8, DATE '2026-06-01', DATE '2027-06-01',
                        'ACTIVE', ?, 'Monday evening', ?)
                """, UUID.randomUUID(), studentId, contractedTutor, subjectId, gradeId, studentUserId, contractRequest);
        application(posting(tutor(), new Posting()), "PENDING", 1);
        application(posting(tutor(), new Posting()), "REJECTED", 10);
        UUID rejectedLongAgo = posting(tutor(), new Posting());
        application(rejectedLongAgo, "REJECTED", 31);
        UUID cancelledByStudent = posting(tutor(), new Posting());
        application(cancelledByStudent, "CANCELLED", 1);
        UUID untouched = posting(tutor(), new Posting());

        assertThat(ids(search(anywhere()))).containsExactlyInAnyOrder(rejectedLongAgo, cancelledByStudent, untouched);
    }

    // ---------------------------------------------------------------- scoring

    @Test
    void ranksBySemanticSimilarityAndStillReturnsPostingsWithoutEmbedding() {
        UUID close = posting(tutor(), new Posting().cosine(0.80));
        UUID medium = posting(tutor(), new Posting().cosine(0.60));
        UUID distant = posting(tutor(), new Posting().cosine(0.35));
        UUID notEmbedded = posting(tutor(), new Posting().cosine(null));

        TutorRecommendationResponse response = search(anywhere());

        List<UUID> embeddedInOrder = ids(response).stream().filter(id -> !id.equals(notEmbedded)).toList();
        assertThat(embeddedInOrder).containsExactly(close, medium, distant);
        assertThat(ids(response)).contains(notEmbedded);
        // a = 0.30 * sem + 0.05 * area + 0.65 * 0.5 (unknown features count as neutral); Q = 0.70 -> x 0.91
        assertThat(score(response, close)).isCloseTo((0.30 * 1.0 + 0.05 + 0.325) * 0.91, within(0.002));
        assertThat(score(response, medium)).isCloseTo((0.30 * 0.6 + 0.05 + 0.325) * 0.91, within(0.002));
        // Only the area is known for the posting without embedding: it ranks below a decent semantic match.
        assertThat(score(response, notEmbedded)).isCloseTo((0.05 + 0.95 * 0.5) * 0.91, within(0.002));
        assertThat(ids(response)).containsExactly(close, medium, notEmbedded, distant);
        assertThat(response.items()).extracting(Item::rank).containsExactly(1, 2, 3, 4);
    }

    @Test
    void fallsBackToTheProfileEmbeddingAndStillAnswersWhenTheAiServiceIsDown() {
        when(queryEmbeddingService.embed(any())).thenReturn(Optional.empty());
        UUID first = posting(tutor(), new Posting().cosine(0.40));
        UUID second = posting(tutor(), new Posting().cosine(0.75));

        assertThat(ids(search(anywhere()))).as("no vector at all: every posting is still returned")
                .containsExactlyInAnyOrder(first, second);

        jdbc.update("UPDATE students SET profile_embedding = CAST(? AS vector) WHERE id = ?", literal(unit(1.0)),
                studentId);
        entityManager.clear();

        assertThat(ids(search(anywhere()))).as("profile vector: nearest first").containsExactly(second, first);
    }

    @Test
    void tutorQualityBreaksTiesBetweenEquallySuitablePostings() {
        UUID average = posting(tutor("VERIFIED", "ACTIVE", "40"), new Posting().cosine(0.70));
        UUID excellent = posting(tutor("VERIFIED", "ACTIVE", "95"), new Posting().cosine(0.70));

        assertThat(ids(search(anywhere()))).containsExactly(excellent, average);
    }

    @Test
    void theLogKeepsTheWeightsUsedAndThePartsOfTheTutorsWillingness() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        UUID posting = posting(tutor(), new Posting().cosine(0.70));

        TutorRecommendationResponse response = search(anywhere());

        entityManager.flush();
        entityManager.clear();
        tools.jackson.databind.JsonNode weights =
                logRepository.findById(response.logId()).orElseThrow().getQueryContext().path("weights");
        assertThat(weights.path("A").path("model").asString()).isEqualTo("A_S2T");
        assertThat(weights.path("A").path("values").path("sem").asDouble()).isEqualTo(0.30);
        assertThat(weights.path("B").path("model").asString()).isEqualTo("B_S2T");
        assertThat(weights.path("B").path("values").path("hist").asDouble()).isEqualTo(0.5);
        assertThat(weights.path("B").path("version").isNull()).isTrue();
        tools.jackson.databind.JsonNode willingness = log(response.logId()).of(posting).path("willingness");
        assertThat(willingness.has("fitSegment")).isTrue();
        assertThat(willingness.has("fitSched")).isTrue();
        assertThat(willingness.has("fitPrice")).isTrue();
    }

    @Test
    void scheduleThatNeverOverlapsAndARecentRejectionPushAPostingDown() {
        UUID overlapping = posting(tutor(), new Posting().cosine(0.60).slot(2, "19:00", "21:00"));
        UUID clashing = posting(tutor(), new Posting().cosine(0.80).slot(5, "08:00", "10:00"));
        UUID rejectingTutor = tutor();
        UUID otherPostingOfRejectingTutor = posting(rejectingTutor, new Posting().cosine(0.80)
                .slot(2, "19:00", "21:00"));
        application(posting(rejectingTutor, new Posting().status("CLOSED")), "REJECTED", 40);

        Filters filters = new Filters(subjectId, gradeId, null, new Area(AreaScope.ANY, null, null), null,
                List.of(new Availability(2, LocalTime.of(19, 0), LocalTime.of(21, 0))), null, null, null);
        TutorRecommendationResponse response = search(filters);

        assertThat(ids(response).get(0)).isEqualTo(overlapping);
        assertThat(ids(response)).containsExactlyInAnyOrder(overlapping, clashing, otherPostingOfRejectingTutor);
        JsonView log = log(response.logId());
        assertThat(log.g(clashing)).isEqualTo(0.4);
        assertThat(log.g(otherPostingOfRejectingTutor)).isEqualTo(0.5);
        assertThat(log.g(overlapping)).isEqualTo(1.0);
        assertThat(log.feature(clashing, "sched")).isEqualTo(0.0);
        assertThat(log.feature(overlapping, "sched")).isEqualTo(1.0);
    }

    @Test
    void usesTagsForNeedStyleAndLevelWhenTheVocabularyIsFilled() {
        UUID examGoal = tag("GOAL", "thi_vao_10_" + UUID.randomUUID(), null);
        String examCode = jdbc.queryForObject("SELECT code FROM recommendation_tags WHERE id = ?", String.class, examGoal);
        UUID patient = tag("STYLE", "kien_nhan_" + UUID.randomUUID(), null);
        String patientCode = jdbc.queryForObject("SELECT code FROM recommendation_tags WHERE id = ?", String.class,
                patient);
        UUID average = tag("SEGMENT", "trung_binh", null);
        entityTag("STUDENT", studentId, average, "SELF");
        UUID strongTutor = tutor();
        entityTag("TUTOR", strongTutor, examGoal, "STRENGTH");
        entityTag("TUTOR", strongTutor, average, "TARGET");
        UUID matching = posting(strongTutor, new Posting().cosine(0.60));
        entityTag("TEACHING_REQUEST", matching, patient, "OFFERED");
        UUID plain = posting(tutor(), new Posting().cosine(0.60));

        TutorRecommendationResponse response = search(new Filters(subjectId, gradeId, null,
                new Area(AreaScope.ANY, null, null), null, null, List.of(patientCode, "khong_ton_tai"),
                List.of(examCode), null));

        assertThat(ids(response)).containsExactly(matching, plain);
        JsonView log = log(response.logId());
        assertThat(log.feature(matching, "need")).isEqualTo(1.0);
        assertThat(log.feature(matching, "style")).isEqualTo(1.0);
        assertThat(log.feature(matching, "segment")).isEqualTo(1.0);
        assertThat(log.feature(plain, "need")).isEqualTo(0.4);
        assertThat(log.feature(plain, "style")).isEqualTo(0.5);
        assertThat(log.feature(plain, "segment")).isEqualTo(0.5);
        assertThat(response.queryContext().path("styleTagCodes")).hasSize(1);
        assertThat(response.queryContext().path("levelTagCode").asString()).isEqualTo("trung_binh");
    }

    @Test
    void flagsLowConfidenceWhenLittleIsKnownAboutTheMatch() {
        posting(tutor(), new Posting().cosine(null).mode("OFFLINE").districts(farDistrictId));
        jdbc.update("UPDATE users SET district_id = NULL WHERE id = ?", studentUserId);
        entityManager.clear();

        TutorRecommendationResponse vague = search(anywhere());
        when(queryEmbeddingService.embed(any())).thenReturn(Optional.empty());
        TutorRecommendationResponse stillVague = search(anywhere());

        assertThat(vague.lowConfidence()).isTrue();
        assertThat(stillVague.lowConfidence()).isTrue();
    }

    // ---------------------------------------------------------------- reciprocal (V2-RC)

    @Test
    void crowdedPostingRanksBelowAFreeOneAndTheLogExplainsWhy() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        UUID crowded = posting(tutor(), new Posting().cosine(0.80).quantity(2));
        applicationOf(otherStudent(), crowded, "ACCEPTED", 3, null);
        for (int index = 0; index < 4; index++) {
            applicationOf(otherStudent(), crowded, "PENDING", 1, null);
        }
        UUID free = posting(tutor(), new Posting().cosine(0.70));

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(free, crowded);
        JsonView log = log(response.logId());
        // 2 places, 1 taken -> r = 1; 4 waiting -> cap = 1 / (1 + 0.5 * 4)
        assertThat(log.willingness(crowded, "cap")).isCloseTo(1 / 3.0, within(0.001));
        assertThat(log.willingness(free, "cap")).isEqualTo(1.0);
        assertThat(log.number(free, "b")).isCloseTo(0.6, within(0.001));
        assertThat(log.number(free, "r")).isCloseTo(2 * 0.615 * 0.6 / (0.615 + 0.6), within(0.002));
    }

    @Test
    void tutorWhoAcceptsSimilarStudentsRanksAboveOneWhoRejectsThem() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        UUID welcomingTutor = tutor();
        UUID pickyTutor = tutor();
        UUID welcoming = posting(welcomingTutor, new Posting().cosine(0.70));
        UUID picky = posting(pickyTutor, new Posting().cosine(0.70));
        UUID oldWelcoming = posting(welcomingTutor, new Posting().status("CLOSED"));
        UUID oldPicky = posting(pickyTutor, new Posting().status("CLOSED"));
        for (int index = 0; index < 4; index++) {
            applicationOf(otherStudent(), oldWelcoming, "ACCEPTED", 20, "150000");
            applicationOf(otherStudent(), oldPicky, "REJECTED", 20, "150000");
        }
        // Too old to count.
        applicationOf(otherStudent(), oldPicky, "ACCEPTED", 200, "150000");

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(welcoming, picky);
        JsonView log = log(response.logId());
        // same grade, same price as the posting, mode ANY -> k = 1 for each of the 4 decisions
        assertThat(log.willingness(welcoming, "hist")).isCloseTo((4 + 1.2) / 6, within(0.001));
        assertThat(log.willingness(picky, "hist")).isCloseTo(1.2 / 6, within(0.001));
    }

    @Test
    void tutorWhoAlreadyProposedToTheStudentsOpenPostingIsCertainToAccept() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        UUID invitingTutor = tutor();
        UUID invited = posting(invitingTutor, new Posting().cosine(0.60));
        UUID other = posting(tutor(), new Posting().cosine(0.60));
        UUID ownRequest = insert("""
                INSERT INTO studying_requests (id, student_id, subject_id, grade_id, learning_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'OPEN')
                """, studentId, subjectId, gradeId);
        insert("""
                INSERT INTO tutor_student_requests (id, tutor_id, grade_id, studying_request_id, teaching_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', 'PENDING')
                """, invitingTutor, gradeId, ownRequest);

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(invited, other);
        JsonView log = log(response.logId());
        assertThat(log.number(invited, "b")).isEqualTo(1.0);
        assertThat(log.number(other, "b")).isCloseTo(0.6, within(0.001));
    }

    @Test
    void platformRateIsMeasuredOnceThereAreEnoughDecisions() {
        UUID old = posting(tutor(), new Posting().status("CLOSED"));
        long acceptedBefore = jdbc.queryForObject(DECISIONS.formatted("= 'ACCEPTED'"), Long.class);
        long decidedBefore = jdbc.queryForObject(DECISIONS.formatted("IN ('ACCEPTED', 'REJECTED')"), Long.class);
        for (int index = 0; index < 3; index++) {
            applicationOf(otherStudent(), old, "ACCEPTED", 10, null);
        }
        applicationOf(otherStudent(), old, "REJECTED", 10, null);
        applicationOf(otherStudent(), old, "REJECTED", 200, null);

        double measured = candidateRepository.platformAcceptanceRate(180, 1, 0.6);
        double tooFew = candidateRepository.platformAcceptanceRate(180, Integer.MAX_VALUE, 0.6);

        assertThat(measured).isCloseTo((acceptedBefore + 3) / (double) (decidedBefore + 4), within(1e-9));
        assertThat(tooFew).isEqualTo(0.6);
    }

    // ---------------------------------------------------------------- log and paging

    @Test
    void writesOneLogWithEveryFeatureOfEveryCandidate() {
        UUID candidate = posting(tutor(), new Posting().cosine(0.55).price("180000"));

        TutorRecommendationResponse response = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(
                        new Filters(subjectId, gradeId, ModePreference.ANY, new Area(AreaScope.ANY, null, null),
                                new Price(null, new BigDecimal("200000")), null, null, null, null),
                        "ôn thi vào 10, gọi 0912345678", "Gia sư Toán 9, mail a@b.vn", null),
                0, 20, null);
        entityManager.flush();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT searcher_student_id, searcher_tutor_id, direction, algorithm_version, query_text,
                       latency_ms, query_context::text AS context, returned_candidate_ids::text AS candidates
                FROM recommendation_logs WHERE id = ?
                """, response.logId());
        assertThat(row.get("searcher_student_id")).isEqualTo(studentId);
        assertThat(row.get("searcher_tutor_id")).isNull();
        assertThat(row.get("direction")).isEqualTo("STUDENT_TO_TUTOR");
        assertThat(row.get("algorithm_version")).isEqualTo(response.algorithmVersion()).isEqualTo("v2.2-rcx");
        assertThat((String) row.get("query_text")).contains("Gia sư Toán 9").doesNotContain("a@b.vn");
        assertThat((Integer) row.get("latency_ms")).isNotNegative();
        assertThat((String) row.get("context")).contains("demandText").contains("ôn thi vào 10")
                .contains("Hình học").doesNotContain("0912345678");
        JsonView log = log(response.logId());
        assertThat(log.feature(candidate, "sem")).isCloseTo(0.5, within(0.002));
        assertThat(log.feature(candidate, "price")).isEqualTo(1.0);
        assertThat(log.feature(candidate, "area")).isEqualTo(1.0);
        assertThat(log.isNull(candidate, "need")).isTrue();
        assertThat(log.isNull(candidate, "hist")).isTrue();
        assertThat(response.queryContext().has("demandText")).isFalse();
        assertThat(response.queryContext().path("semanticText").asString()).isEqualTo("ôn thi vào 10, gọi");
    }

    @Test
    void laterPagesAreServedFromTheLogInTheSameOrderEvenIfDataChanged() {
        UUID first = posting(tutor(), new Posting().cosine(0.80));
        UUID second = posting(tutor(), new Posting().cosine(0.70));
        UUID third = posting(tutor(), new Posting().cosine(0.60));
        UUID fourth = posting(tutor(), new Posting().cosine(0.50));
        UUID fifth = posting(tutor(), new Posting().cosine(0.40));

        TutorRecommendationResponse pageOne = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(anywhere(), null, null, null), 0, 2, null);
        entityManager.flush();
        // Between the two requests the best posting is closed and the worst one becomes the best match.
        jdbc.update("UPDATE teaching_requests SET status = 'CLOSED' WHERE id = ?", first);
        jdbc.update("UPDATE teaching_requests SET embedding = CAST(? AS vector) WHERE id = ?", literal(unit(1.0)), fifth);
        entityManager.clear();
        when(queryEmbeddingService.embed(any())).thenThrow(new AssertionError("a later page must not embed again"));
        TutorRecommendationResponse pageTwo = service.recommendTutors(studentUserId, null, 1, 2, pageOne.logId());
        TutorRecommendationResponse pageThree = service.recommendTutors(studentUserId, null, 2, 2, pageOne.logId());

        assertThat(ids(pageOne)).containsExactly(first, second);
        assertThat(pageOne.totalElements()).isEqualTo(5);
        assertThat(ids(pageTwo)).containsExactly(third, fourth);
        assertThat(pageTwo.items()).extracting(Item::rank).containsExactly(3, 4);
        assertThat(pageTwo.logId()).isEqualTo(pageOne.logId());
        assertThat(pageTwo.totalElements()).isEqualTo(5);
        assertThat(pageTwo.items().get(0).score()).isCloseTo((0.30 * 0.6 + 0.05 + 0.325) * 0.91, within(0.002));
        assertThat(ids(pageThree)).containsExactly(fifth);
        assertThat(logRepository.count()).as("paging does not write another log").isEqualTo(countLogsBefore() + 1);
    }

    @Test
    void anotherStudentCannotReadSomeoneElsesSearch() {
        posting(tutor(), new Posting());
        UUID logId = search(anywhere()).logId();
        UUID otherUser = user(homeDistrictId);
        insert("INSERT INTO students (id, user_id) VALUES (?, ?)", otherUser);

        assertThatThrownBy(() -> service.recommendTutors(otherUser, null, 0, 20, logId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("RECOMMENDATION_LOG_NOT_FOUND");
                });
    }

    @Test
    void keepsAtMostTheConfiguredNumberOfCandidatesInTheLog() {
        for (int index = 0; index < settings.getLogCandidateLimit() + 3; index++) {
            posting(tutor(), new Posting().cosine(0.40 + index * 0.005));
        }

        TutorRecommendationResponse response = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(anywhere(), null, null, null), 0, 50, null);

        assertThat(response.totalElements()).isEqualTo(settings.getLogCandidateLimit());
        assertThat(response.items()).hasSize(50);
        // Two places of the first page are exploration slots; everything else is in score order.
        assertThat(response.items()).filteredOn(item -> !item.isExploration()).extracting(Item::score)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    // ---------------------------------------------------------------- helpers

    private long logsBefore = -1;

    // ---- one result per tutor, reasons, suggestions

    @Test
    void showsATutorOnceWithTheirBestPosting() {
        UUID busyTutor = tutor();
        UUID best = posting(busyTutor, new Posting().cosine(0.80));
        posting(busyTutor, new Posting().cosine(0.70));
        posting(busyTutor, new Posting().cosine(0.50));
        UUID other = posting(tutor(), new Posting().cosine(0.60));

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(best, other);
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    void saysWhyAPostingFitsAndRepeatsItOnLaterPages() {
        UUID affordable = posting(tutor(), new Posting().cosine(0.80).price("180000"));
        UUID expensive = posting(tutor(), new Posting().cosine(0.45).price("230000"));
        Filters filters = new Filters(subjectId, gradeId, null, new Area(AreaScope.ANY, null, null),
                new Price(null, new BigDecimal("200000")), null, null, null, null);

        TutorRecommendationResponse response = search(filters);

        assertThat(ids(response)).containsExactly(affordable, expensive);
        assertThat(response.items().get(0).reasons()).containsExactly(
                "Nội dung tin đăng rất sát với nhu cầu của bạn", "Học phí 180.000đ — nằm trong ngân sách");
        assertThat(response.items().get(0).caution()).isNull();
        assertThat(response.items().get(1).reasons()).isEmpty();
        assertThat(response.items().get(1).caution()).isEqualTo("Học phí cao hơn ngân sách 30.000đ");

        TutorRecommendationResponse again = service.recommendTutors(studentUserId, null, 0, 20, response.logId());
        assertThat(again.items().get(0).reasons()).isEqualTo(response.items().get(0).reasons());
        assertThat(again.items().get(1).caution()).isEqualTo("Học phí cao hơn ngân sách 30.000đ");
    }

    @Test
    void suggestsLooseningTheSearchWhenFewGoodTutorsAreFoundAndApplyingItFindsThem() {
        UUID atHome = posting(tutor(), new Posting().mode("OFFLINE").districts(homeDistrictId).cosine(0.80));
        UUID near = posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId).cosine(0.80));
        UUID alsoNear = posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId).cosine(0.80));
        posting(tutor(), new Posting().mode("OFFLINE").districts(farDistrictId).cosine(0.80));
        posting(tutor(), new Posting().mode("OFFLINE").districts(farDistrictId).cosine(0.80));

        TutorRecommendationResponse response =
                search(area(AreaScope.DISTRICTS, List.of(homeDistrictId), null));

        assertThat(ids(response)).as("suggestions never change the results").containsExactly(atHome);
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::code)
                .containsExactly("AREA_ANY", "AREA_PROVINCE");
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::message).containsExactly(
                "Bỏ giới hạn khu vực: thêm 4 gia sư phù hợp", "Mở rộng ra toàn Tỉnh A: thêm 2 gia sư phù hợp");
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::gain).containsExactly(4, 2);

        tools.jackson.databind.JsonNode apply = response.relaxations().get(1).apply().path("area");
        assertThat(apply.path("scope").asString()).isEqualTo("PROVINCE");
        TutorRecommendationResponse widened = search(area(AreaScope.valueOf(apply.path("scope").asString()),
                null, UUID.fromString(apply.path("provinceId").asString())));
        assertThat(ids(widened)).containsExactlyInAnyOrder(atHome, near, alsoNear);

        assertThat(service.recommendTutors(studentUserId, null, 0, 20, response.logId()).relaxations())
                .as("a later page repeats the suggestions of the search")
                .extracting(RelaxationSuggestion::code).containsExactly("AREA_ANY", "AREA_PROVINCE");
    }

    @Test
    void suggestsARaisedBudgetOrAFlexibleScheduleOnlyWhenThatReallyHelps() {
        posting(tutor(), new Posting().cosine(0.80).price("350000"));
        posting(tutor(), new Posting().cosine(0.80).price("360000"));
        Filters filters = new Filters(subjectId, gradeId, null, new Area(AreaScope.ANY, null, null),
                new Price(null, new BigDecimal("200000")),
                List.of(new Availability(2, LocalTime.of(19, 0), LocalTime.of(21, 0))), null, null, null);

        TutorRecommendationResponse response = search(filters);

        // 350k and 360k are above 1.5 x 200k (filtered out) and above 1.5 x 230k, but within 1.5 x 260k.
        assertThat(response.items()).isEmpty();
        assertThat(response.relaxations()).extracting(RelaxationSuggestion::code).containsExactly("PRICE_30");
        assertThat(response.relaxations().get(0).message())
                .isEqualTo("Nếu ngân sách 260.000đ: thêm 2 gia sư phù hợp");
        assertThat(response.relaxations().get(0).apply().path("price").path("max").decimalValue())
                .isEqualByComparingTo("260000");
    }

    @Test
    void givesNoSuggestionsWhenSwitchedOff() {
        ReflectionTestUtils.setField(settings, "relaxationEnabled", false);
        posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId).cosine(0.80));
        posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId).cosine(0.80));

        assertThat(search(area(AreaScope.DISTRICTS, List.of(homeDistrictId), null)).relaxations()).isEmpty();
    }

    // ---- saved searches and match alerts (C7)

    @Test
    void savesASearchRunsItByIdAndLetsTheOwnerRenameOrDeleteIt() {
        UUID match = posting(tutor(), new Posting().mode("OFFLINE").districts(nearDistrictId));
        posting(tutor(), new Posting().mode("OFFLINE").districts(farDistrictId));

        SavedSearchResponse saved = savedSearchService.create(studentUserId, new SavedSearchRequest(
                RecommendationDirection.STUDENT_TO_TUTOR, "  Toán 9 gần nhà  ",
                jsonMapper.valueToTree(area(AreaScope.DISTRICTS, List.of(nearDistrictId), null)),
                "ôn thi, gọi 0912345678", null));
        entityManager.flush();

        assertThat(saved.name()).isEqualTo("Toán 9 gần nhà");
        assertThat(saved.notifyEnabled()).as("notifications are on unless switched off").isTrue();
        assertThat(saved.semanticText()).isEqualTo("ôn thi, gọi");
        assertThat(saved.filters().path("subjectId").asString()).isEqualTo(subjectId.toString());
        assertThat(savedSearchService.findMine(studentUserId)).extracting(SavedSearchResponse::id)
                .containsExactly(saved.id());

        // Opening it: the saved criteria are the search, the filters in the body are ignored.
        TutorRecommendationResponse response = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(anywhere(), null, null, null, saved.id()), 0, 20, null);

        assertThat(ids(response)).containsExactly(match);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT saved_search_id FROM recommendation_logs WHERE id = ?", UUID.class,
                response.logId())).isEqualTo(saved.id());

        SavedSearchResponse renamed = savedSearchService.update(
                studentUserId, saved.id(), new SavedSearchUpdateRequest("Gần nhà", false));
        assertThat(renamed.name()).isEqualTo("Gần nhà");
        assertThat(renamed.notifyEnabled()).isFalse();
        assertThat(savedSearchService.update(studentUserId, saved.id(), new SavedSearchUpdateRequest(null, null)).name())
                .as("only what is sent changes").isEqualTo("Gần nhà");

        assertThatThrownBy(() -> savedSearchService.delete(UUID.randomUUID(), saved.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SAVED_SEARCH_NOT_FOUND"));
        // A later request: the log written above is no longer in the persistence context.
        entityManager.flush();
        entityManager.clear();
        savedSearchService.delete(studentUserId, saved.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(savedSearchService.findMine(studentUserId)).isEmpty();
        assertThatThrownBy(() -> service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(null, null, null, null, saved.id()), 0, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SAVED_SEARCH_NOT_FOUND"));
    }

    @Test
    void refusesSearchesThatCouldNotBeRunAndMoreThanFivePerUser() {
        tools.jackson.databind.JsonNode usable = jsonMapper.valueToTree(anywhere());
        assertSaveRejected("SAVED_SEARCH_INCOMPLETE", RecommendationDirection.STUDENT_TO_TUTOR,
                jsonMapper.createObjectNode());
        assertSaveRejected("SAVED_SEARCH_DIRECTION_NOT_ALLOWED", RecommendationDirection.TUTOR_TO_STUDENT, usable);
        assertSaveRejected("INVALID_SUBJECT", RecommendationDirection.STUDENT_TO_TUTOR,
                jsonMapper.valueToTree(new Filters(UUID.randomUUID(), gradeId, null, null, null, null, null, null, null)));
        assertSaveRejected("INVALID_SAVED_SEARCH", RecommendationDirection.STUDENT_TO_TUTOR,
                jsonMapper.valueToTree(new Filters(subjectId, gradeId, null, null,
                        new Price(new BigDecimal("300000"), new BigDecimal("100000")), null, null, null, null)));

        for (int index = 0; index < 5; index++) {
            savedSearchService.create(studentUserId, new SavedSearchRequest(
                    RecommendationDirection.STUDENT_TO_TUTOR, "Tìm " + index, usable, null, true));
        }
        entityManager.flush();
        assertSaveRejected("SAVED_SEARCH_LIMIT_REACHED", RecommendationDirection.STUDENT_TO_TUTOR, usable);

        savedSearchService.delete(studentUserId, savedSearchService.findMine(studentUserId).get(0).id());
        entityManager.flush();
        assertThat(savedSearchService.create(studentUserId, new SavedSearchRequest(
                RecommendationDirection.STUDENT_TO_TUTOR, "Thay thế", usable, null, true)).id()).isNotNull();
    }

    @Test
    void tellsAStudentAboutANewPostingOnlyWhenItFitsVeryWellAndOnlyOncePerDay() {
        UUID ownRequest = insert("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, learning_mode, status, max_price)
                VALUES (?, ?, ?, ?, 'ONLINE', 'OPEN', 200000)
                """, studentId, subjectId, gradeId);
        SavedSearchResponse saved = savedSearchService.create(studentUserId, new SavedSearchRequest(
                RecommendationDirection.STUDENT_TO_TUTOR, "Toán 9", jsonMapper.valueToTree(anywhere()), null, true));
        UUID weak = posting(tutor("VERIFIED", "ACTIVE", "0"), new Posting().cosine(0.35));
        UUID great = posting(tutor("VERIFIED", "ACTIVE", "100"), new Posting().cosine(0.80).price("180000"));
        entityManager.flush();

        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, weak))
                .as("first in the list, but far from a very good match").isZero();
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, great))
                .as("two sources of the same student, one notification").isEqualTo(1);

        org.mockito.ArgumentCaptor<NotificationDomainEvent> sent =
                org.mockito.ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(notificationService, times(1)).createFromEvent(sent.capture());
        NotificationDomainEvent event = sent.getValue();
        assertThat(event.recipientUserId()).isEqualTo(studentUserId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.RECOMMENDATION_MATCH_FOUND);
        assertThat(event.referenceType().name()).isEqualTo("TEACHING_REQUEST");
        assertThat(event.referenceId()).isEqualTo(great.toString());
        assertThat(event.deduplicationKey()).startsWith("rec-match:").endsWith(":" + great);
        assertThat(event.data()).containsEntry("title", "Dạy kèm");
        assertThat(event.data().get("reason")).isNotBlank();
        Map<String, Object> alert = jdbc.queryForMap(
                "SELECT recipient_user_id, candidate_rank, final_score FROM recommendation_match_alerts WHERE candidate_id = ?",
                great);
        assertThat(alert.get("recipient_user_id")).isEqualTo(studentUserId);
        assertThat(alert.get("candidate_rank")).isEqualTo(1);
        assertThat((BigDecimal) alert.get("final_score")).isGreaterThanOrEqualTo(new BigDecimal("0.65"));

        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, great))
                .as("nothing more within the day").isZero();

        // A day later the other source may speak up, but never the same one about the same posting.
        ReflectionTestUtils.setField(settings, "alertCooldownHours", 0);
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, great)).isEqualTo(1);
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, great)).isZero();
        assertThat(jdbc.queryForList(
                "SELECT source_type FROM recommendation_match_alerts WHERE candidate_id = ?", String.class, great))
                .containsExactlyInAnyOrder("STUDYING_REQUEST", "SAVED_SEARCH");
        assertThat(jdbc.queryForObject(
                "SELECT last_notified_at IS NOT NULL FROM recommendation_saved_searches WHERE id = ?", Boolean.class,
                saved.id())).isTrue();
        assertThat(ownRequest).isNotNull();
    }

    @Test
    void staysQuietAboutAGoodPostingOutsideTheTopThreeOrWhenNotificationsAreOff() {
        SavedSearchResponse saved = savedSearchService.create(studentUserId, new SavedSearchRequest(
                RecommendationDirection.STUDENT_TO_TUTOR, "Toán 9", jsonMapper.valueToTree(anywhere()), null, true));
        for (int index = 0; index < 3; index++) {
            posting(tutor("VERIFIED", "ACTIVE", "100"), new Posting().cosine(0.80));
        }
        UUID fourth = posting(tutor("VERIFIED", "ACTIVE", "100"), new Posting().cosine(0.79));
        entityManager.flush();

        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, fourth)).isZero();

        ReflectionTestUtils.setField(settings, "alertTopRank", 4);
        savedSearchService.update(studentUserId, saved.id(), new SavedSearchUpdateRequest(null, false));
        entityManager.flush();
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, fourth))
                .as("notifications switched off for this search").isZero();

        savedSearchService.update(studentUserId, saved.id(), new SavedSearchUpdateRequest(null, true));
        entityManager.flush();
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, fourth)).isEqualTo(1);

        ReflectionTestUtils.setField(settings, "alertEnabled", false);
        UUID later = posting(tutor("VERIFIED", "ACTIVE", "100"), new Posting().cosine(0.80));
        ReflectionTestUtils.setField(settings, "alertCooldownHours", 0);
        assertThat(matchAlertService.process(RecommendationEntityType.TEACHING_REQUEST, later)).isZero();
        ReflectionTestUtils.setField(settings, "alertEnabled", true);
    }

    private void assertSaveRejected(
            String code, RecommendationDirection direction, tools.jackson.databind.JsonNode filters) {
        assertThatThrownBy(() -> savedSearchService.create(studentUserId,
                new SavedSearchRequest(direction, "Tên", filters, null, true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    // ---- events, exposure statistics, exploration slots

    @Test
    void recordsWhatTheStudentDoesWithAResultAndQueuesOneProfileUpdate() {
        UUID tutorId = tutor();
        UUID posting = posting(tutorId, new Posting());
        TutorRecommendationResponse response = search(anywhere());
        jdbc.update("DELETE FROM ai_enrichment_jobs WHERE job_type = 'PROFILE_UPDATE'");

        eventService.record(studentUserId, response.logId(), event(RecommendationAction.VIEWED, 99, posting));
        eventService.record(studentUserId, response.logId(), event(RecommendationAction.FAVORITED, null, posting));
        eventService.record(studentUserId, response.logId(), event(RecommendationAction.FAVORITED, null, posting));
        entityManager.flush();

        List<Map<String, Object>> clicks = jdbc.queryForList("""
                SELECT action, rank, clicked_tutor_id, clicked_student_id, candidate_teaching_request_id
                FROM recommendation_click_logs WHERE recommendation_log_id = ? ORDER BY created_at, action DESC
                """, response.logId());
        assertThat(clicks).hasSize(3).allSatisfy(click -> {
            assertThat(click.get("rank")).as("the rank of the search, not the one the client sent").isEqualTo(1);
            assertThat(click.get("clicked_tutor_id")).isEqualTo(tutorId);
            assertThat(click.get("clicked_student_id")).isNull();
            assertThat(click.get("candidate_teaching_request_id")).isEqualTo(posting);
        });
        assertThat(clicks).extracting(click -> click.get("action")).containsExactlyInAnyOrder(
                "VIEWED", "FAVORITED", "FAVORITED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM favorite_tutors WHERE student_id = ? AND tutor_id = ? AND deleted_at IS NULL",
                Integer.class, studentId, tutorId)).as("liking twice keeps one favourite").isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT entity_type || ':' || entity_id || ':' || status FROM ai_enrichment_jobs WHERE job_type = 'PROFILE_UPDATE'",
                String.class)).as("three events, one rebuild").containsExactly("STUDENT:" + studentId + ":PENDING");

        assertThatThrownBy(() -> eventService.record(studentUserId, response.logId(),
                event(RecommendationAction.VIEWED, 1, UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CANDIDATE_NOT_IN_RESULTS"));
        assertThatThrownBy(() -> eventService.record(UUID.randomUUID(), response.logId(),
                event(RecommendationAction.VIEWED, 1, posting)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("RECOMMENDATION_LOG_NOT_FOUND"));
    }

    @Test
    void countsFirstPageImpressionsAndOneClickPerSearch() {
        UUID opened = posting(tutor(), new Posting().cosine(0.80));
        UUID ignored = posting(tutor(), new Posting().cosine(0.70));
        TutorRecommendationResponse first = search(anywhere());
        search(anywhere());
        eventService.record(studentUserId, first.logId(), event(RecommendationAction.VIEWED, null, opened));
        eventService.record(studentUserId, first.logId(), event(RecommendationAction.FAVORITED, null, opened));
        eventService.record(studentUserId, first.logId(), event(RecommendationAction.DISMISSED, null, ignored));
        entityManager.flush();

        exposureStatsJob.run();

        assertThat(exposure(opened)).isEqualTo("2/1 this week, 2/1 in total");
        assertThat(exposure(ignored)).as("a dismissal is not a click").isEqualTo("2/0 this week, 2/0 in total");
    }

    @Test
    void givesTwoPlacesOfTheFirstPageToRarelyShownPostingsAndSaysSo() {
        List<UUID> byScore = new java.util.ArrayList<>();
        for (int index = 0; index < 30; index++) {
            byScore.add(posting(tutor(), new Posting().cosine(0.80 - 0.005 * index)));
        }

        TutorRecommendationResponse response = search(anywhere());

        assertThat(response.totalElements()).isEqualTo(30);
        assertThat(ids(response)).hasSize(20).doesNotHaveDuplicates();
        assertThat(response.items()).filteredOn(Item::isExploration).hasSize(2);
        for (int place : List.of(4, 11)) {
            Item item = response.items().get(place);
            assertThat(item.isExploration()).isTrue();
            assertThat(byScore.indexOf(item.teachingRequestId())).as("from below the first page")
                    .isGreaterThanOrEqualTo(20);
            assertThat(item.reasons().get(0)).isEqualTo("Gia sư mới trên nền tảng");
        }
        assertThat(ids(response).subList(0, 4)).containsExactlyElementsOf(byScore.subList(0, 4));

        TutorRecommendationResponse again = service.recommendTutors(studentUserId, null, 0, 20, response.logId());
        assertThat(ids(again)).containsExactlyElementsOf(ids(response));
        assertThat(again.items().get(4).isExploration()).isTrue();
        assertThat(again.items().get(11).isExploration()).isTrue();

        ReflectionTestUtils.setField(settings, "explorationEnabled", false);
        TutorRecommendationResponse strict = search(anywhere());
        assertThat(strict.items()).noneMatch(Item::isExploration);
        assertThat(ids(strict)).containsExactlyElementsOf(byScore.subList(0, 20));
    }

    // ---- preference profile (H1)

    @Test
    void whatTheStudentLikedBeforeLiftsSimilarPostingsUntilTheHistoryIsDeleted() {
        String code = "thu_luyen_de_" + UUID.randomUUID().toString().substring(0, 8);
        UUID style = tag("STYLE", code, null);
        UUID likedTutor = tutor();
        UUID liked = posting(likedTutor, new Posting().cosine(0.60));
        entityTag("TUTOR", likedTutor, style, "OFFERED");
        UUID plain = posting(tutor(), new Posting().cosine(0.60));
        TutorRecommendationResponse before = search(anywhere());
        assertThat(score(before, liked)).isEqualTo(score(before, plain));
        assertThat(log(before.logId()).isNull(liked, "hist")).isTrue();

        eventService.record(studentUserId, before.logId(), event(RecommendationAction.FAVORITED, null, liked));
        entityManager.flush();
        assertThat(preferenceProfileService.recompute(RecommendationProfileRole.STUDENT, studentId)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> profile = jdbc.queryForMap(
                "SELECT role, signal_count, tag_affinity ->> ? AS affinity, preference_embedding IS NOT NULL AS has_vector"
                        + " FROM recommendation_user_profiles WHERE user_id = ?", code, studentUserId);
        assertThat(profile).containsEntry("role", "STUDENT").containsEntry("signal_count", 1)
                .containsEntry("has_vector", true);
        assertThat(Double.parseDouble((String) profile.get("affinity"))).isCloseTo(1.0, within(1e-9));

        TutorRecommendationResponse after = search(anywhere());

        assertThat(ids(after)).containsExactly(liked, plain);
        JsonView log = log(after.logId());
        // one signal of the ten needed: w_hist = 0.15 x 1 / 10
        assertThat(log.number(liked, "wHist")).isCloseTo(0.015, within(1e-9));
        // same vector as the liked posting (0.6 x 1) plus its tag (0.4 x 1 / max(1, 1))
        assertThat(log.feature(liked, "hist")).isCloseTo(1.0, within(1e-4));
        assertThat(log.feature(plain, "hist")).isCloseTo(0.6, within(1e-4));
        assertThat(score(after, liked)).isGreaterThan(score(after, plain));
        assertThat(after.items().get(0).reasons()).contains("Giống các gia sư bạn đã quan tâm: Tên " + code);

        preferenceProfileService.deleteHistory(studentUserId);
        entityManager.flush();
        entityManager.clear();
        TutorRecommendationResponse forgotten = search(anywhere());

        assertThat(score(forgotten, liked)).isEqualTo(score(forgotten, plain));
        assertThat(log(forgotten.logId()).number(liked, "wHist")).isZero();
        // Rebuilding the profile must not bring back what the student asked to forget.
        preferenceProfileService.recompute(RecommendationProfileRole.STUDENT, studentId);
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                "SELECT signal_count FROM recommendation_user_profiles WHERE user_id = ?", Integer.class,
                studentUserId)).isZero();
    }

    // ---- in-session refinement (B5)

    @Test
    void reordersOnlyTheUnseenResultsTowardsWhatTheStudentOpened() {
        UUID first = posting(tutor("VERIFIED", "ACTIVE", "0"), new Posting().cosine(0.75));
        UUID opened = posting(tutor("VERIFIED", "ACTIVE", "100"), new Posting().cosine(0.35));
        UUID third = posting(tutor("VERIFIED", "ACTIVE", "0"), new Posting().cosine(0.55));
        UUID fourth = posting(tutor("VERIFIED", "ACTIVE", "40"), new Posting().cosine(0.40));
        TutorRecommendationResponse response = search(anywhere());
        assertThat(ids(response)).containsExactly(first, opened, third, fourth);

        TutorRecommendationResponse untouched =
                (TutorRecommendationResponse) refineService.refine(studentUserId, response.logId(), 2);
        assertThat(ids(untouched)).as("nothing opened yet: nothing to learn from").containsExactly(third, fourth);

        eventService.record(studentUserId, response.logId(), event(RecommendationAction.VIEWED, null, opened));
        entityManager.flush();
        TutorRecommendationResponse refined =
                (TutorRecommendationResponse) refineService.refine(studentUserId, response.logId(), 2);

        // The opened posting lies far from the typed query; the fourth one is nearer to it than the third.
        assertThat(ids(refined)).containsExactly(fourth, third);
        assertThat(refined.items()).extracting(Item::rank).containsExactly(3, 4);
        // q' = normalize(q + 0.6 x opened - 0.3 x first), first being passed over: (0.938, 0.346)
        // fourth: s' = 0.785, f_sem = 0.5 x 0.2 + 0.5 x 0.785, a = 0.435 + 0.3 x 0.293, x (0.7 + 0.3 x 0.4)
        assertThat(score(refined, fourth)).isCloseTo(0.429, within(2e-3));
        // third: s' = 1, f_sem = 0.75, a = 0.525 + 0.3 x 0.25, x 0.7
        assertThat(score(refined, third)).isCloseTo(0.420, within(2e-3));

        TutorRecommendationResponse page = service.recommendTutors(studentUserId, null, 0, 20, response.logId());
        assertThat(ids(page)).as("what was on screen stays, the rest follows the new order")
                .containsExactly(first, opened, fourth, third);
        assertThat(score(page, first)).isCloseTo(score(response, first), within(1e-3));

        TutorRecommendationResponse twice =
                (TutorRecommendationResponse) refineService.refine(studentUserId, response.logId(), 2);
        assertThat(score(twice, fourth)).as("refining again does not compound").isEqualTo(score(refined, fourth));

        assertThatThrownBy(() -> refineService.refine(UUID.randomUUID(), response.logId(), 2))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("RECOMMENDATION_LOG_NOT_FOUND"));
    }

    private RecommendationEventRequest event(RecommendationAction action, Integer rank, UUID candidateId) {
        return new RecommendationEventRequest(action, rank, candidateId);
    }

    private String exposure(UUID teachingRequestId) {
        return jdbc.queryForObject("""
                SELECT impressions_7d || '/' || clicks_7d || ' this week, ' || impressions_total || '/' || clicks_total
                       || ' in total'
                FROM recommendation_exposure_stats WHERE candidate_type = 'TEACHING_REQUEST' AND candidate_id = ?
                """, String.class, teachingRequestId);
    }

    // ---- phase 4: rerank

    @Test
    void reranksTheBestPostingsStoresTheScoresAndReusesThemOnTheNextSearch() {
        UUID nearestByVector = posting(tutor(), new Posting().cosine(0.75));
        UUID mostRelevant = posting(tutor(), new Posting().cosine(0.55));
        title(nearestByVector, "Luyện chữ đẹp");
        title(mostRelevant, "Luyện thi vào 10, mạnh hình học");
        crossEncoderPrefers("hình học");

        TutorRecommendationResponse first = search(anywhere());

        assertThat(ids(first)).as("phase 3 alone would put the nearest vector first")
                .containsExactly(mostRelevant, nearestByVector);
        assertThat(first.queryContext().has("rerankStatus")).isFalse();
        JsonView log = log(first.logId());
        assertThat(log.number(mostRelevant, "rerank")).isCloseTo(0.95, within(1e-9));
        // s = (0.55 - 0.30) / 0.50 = 0.5, f_sem = 0.3 * 0.5 + 0.7 * 0.95
        assertThat(log.feature(mostRelevant, "sem")).isCloseTo(0.815, within(1e-4));
        assertThat(log.feature(nearestByVector, "sem")).isCloseTo(0.3 * 0.9 + 0.7 * 0.05, within(1e-4));
        assertThat(rerankStatus(first.logId())).isEqualTo("ONLINE");

        TutorRecommendationResponse second = search(anywhere());

        assertThat(ids(second)).containsExactly(mostRelevant, nearestByVector);
        assertThat(score(second, mostRelevant)).isEqualTo(score(first, mostRelevant));
        assertThat(rerankStatus(second.logId())).isEqualTo("CACHE");
        verify(aiServiceClient, times(1)).rerank(any(), any(), any());

        // An edited posting is scored again; the other one still comes from the cache.
        title(nearestByVector, "Luyện chữ đẹp và hình học");
        entityManager.clear();
        TutorRecommendationResponse third = search(anywhere());

        assertThat(ids(third)).containsExactly(nearestByVector, mostRelevant);
        assertThat(rerankStatus(third.logId())).isEqualTo("ONLINE");
        org.mockito.ArgumentCaptor<List<String>> documents = org.mockito.ArgumentCaptor.captor();
        verify(aiServiceClient, times(2)).rerank(any(), documents.capture(), any());
        assertThat(documents.getAllValues().get(1)).singleElement().asString().contains("Luyện chữ đẹp và hình học");
    }

    @Test
    void keepsTheOrderOfPhaseThreeWhenTheCrossEncoderDoesNotAnswer() {
        UUID nearestByVector = posting(tutor(), new Posting().cosine(0.75));
        UUID other = posting(tutor(), new Posting().cosine(0.55));

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(nearestByVector, other);
        assertThat(rerankStatus(response.logId())).isEqualTo("TIMEOUT");
        assertThat(log(response.logId()).of(nearestByVector).path("rerank").isNull()).isTrue();
    }

    @Test
    void inAsyncModeASearchDoesNotWaitAndTheNextOneUsesWhatTheWorkerScored() {
        ReflectionTestUtils.setField(settings, "rerankMode", RerankMode.ASYNC);
        UUID nearestByVector = posting(tutor(), new Posting().cosine(0.75));
        UUID mostRelevant = posting(tutor(), new Posting().cosine(0.55));
        title(mostRelevant, "Luyện thi vào 10, mạnh hình học");
        crossEncoderPrefers("hình học");

        TutorRecommendationResponse first = search(anywhere());

        assertThat(ids(first)).as("the cosine part alone").containsExactly(nearestByVector, mostRelevant);
        assertThat(rerankStatus(first.logId())).isEqualTo("NONE");
        verify(aiServiceClient, never()).rerank(any(), any(), any());
        assertThat(rerankQueue.size()).isEqualTo(2);

        search(anywhere());

        assertThat(rerankQueue.size()).as("a pair is queued once").isEqualTo(2);
        backgroundWork.forEach(Runnable::run);
        assertThat(rerankQueue.size()).isZero();
        verify(aiServiceClient, times(1)).rerank(any(), any(), any());

        TutorRecommendationResponse third = search(anywhere());

        assertThat(ids(third)).containsExactly(mostRelevant, nearestByVector);
        assertThat(rerankStatus(third.logId())).isEqualTo("CACHE");
        verify(aiServiceClient, times(1)).rerank(any(), any(), any());
    }

    @Test
    void logsWhenTheQueryTextCouldNotBeEmbeddedAndTheProfileVectorWasUsed() {
        posting(tutor(), new Posting().cosine(0.75));

        TutorRecommendationResponse embedded = search(anywhere());
        when(queryEmbeddingService.embed(any())).thenReturn(Optional.empty());
        TutorRecommendationResponse fellBack = search(anywhere());

        assertThat(queryVector(embedded.logId())).isEqualTo("QUERY");
        assertThat(queryVector(fellBack.logId())).isEqualTo("PROFILE_FALLBACK");
        assertThat(fellBack.queryContext().has("queryVector")).as("internal, like rerankStatus").isFalse();
    }

    private String queryVector(UUID logId) {
        return logRepository.findById(logId).orElseThrow().getQueryContext().path("queryVector").asString();
    }

    @Test
    void skipsRerankingWhenSwitchedOff() {
        ReflectionTestUtils.setField(settings, "rerankEnabled", false);
        UUID nearestByVector = posting(tutor(), new Posting().cosine(0.75));
        UUID other = posting(tutor(), new Posting().cosine(0.55));
        crossEncoderPrefers("Dạy kèm");

        TutorRecommendationResponse response = search(anywhere());

        assertThat(ids(response)).containsExactly(nearestByVector, other);
        assertThat(rerankStatus(response.logId())).isEqualTo("DISABLED");
        verify(aiServiceClient, times(0)).rerank(any(), any(), any());
    }

    @Test
    void prefetchScoresAheadWhatASearchFromTheStudentsPostingNeeds() {
        UUID ownRequest = insert("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, learning_mode, status, max_price)
                VALUES (?, ?, ?, ?, 'ONLINE', 'OPEN', 200000)
                """, studentId, subjectId, gradeId);
        UUID nearestByVector = posting(tutor(), new Posting().cosine(0.75));
        UUID mostRelevant = posting(tutor(), new Posting().cosine(0.55));
        title(mostRelevant, "Luyện thi vào 10, mạnh hình học");
        crossEncoderPrefers("hình học");
        long logs = logRepository.count();

        int computed = service.prefetchRerank(studentUserId, ownRequest);

        assertThat(computed).isEqualTo(2);
        assertThat(logRepository.count()).as("a prefetch is not a search").isEqualTo(logs);
        assertThat(service.prefetchRerank(studentUserId, ownRequest)).as("nothing left to score").isZero();

        TutorRecommendationResponse response = service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(null, null, null, ownRequest), 0, 20, null);

        assertThat(ids(response)).containsExactly(mostRelevant, nearestByVector);
        assertThat(rerankStatus(response.logId())).isEqualTo("CACHE");
        verify(aiServiceClient, times(1)).rerank(any(), any(), any());
        assertThat(service.prefetchRerank(UUID.randomUUID(), ownRequest)).as("unknown student").isZero();
    }

    private void title(UUID teachingRequestId, String title) {
        jdbc.update("UPDATE teaching_requests SET title = ? WHERE id = ?", title, teachingRequestId);
    }

    /** The cross-encoder is replaced by: 0.95 for a posting that mentions the phrase, 0.05 otherwise. */
    private void crossEncoderPrefers(String phrase) {
        when(aiServiceClient.rerank(any(), any(), any())).thenAnswer(invocation -> {
            List<String> documents = invocation.getArgument(1);
            return Optional.of(new RerankResult("bge-reranker-v2-m3-int8",
                    documents.stream().map(document -> document.contains(phrase) ? 0.95 : 0.05).toList()));
        });
    }

    private String rerankStatus(UUID logId) {
        entityManager.flush();
        entityManager.clear();
        return logRepository.findById(logId).orElseThrow().getQueryContext().path("rerankStatus").asString();
    }

    private long countLogsBefore() {
        return logsBefore;
    }

    @BeforeEach
    void rememberLogCount() {
        logsBefore = logRepository.count();
    }

    private TutorRecommendationResponse search(Filters filters) {
        return service.recommendTutors(studentUserId,
                new TutorRecommendationRequest(filters, null, null, null), 0, 20, null);
    }

    private double score(TutorRecommendationResponse response, UUID teachingRequestId) {
        return response.items().stream().filter(item -> item.teachingRequestId().equals(teachingRequestId))
                .findFirst().orElseThrow().score();
    }

    private List<UUID> ids(TutorRecommendationResponse response) {
        return response.items().stream().map(Item::teachingRequestId).toList();
    }

    private Filters filters() {
        return new Filters(subjectId, gradeId, null, null, null, null, null, null, null);
    }

    private Filters anywhere() {
        return area(AreaScope.ANY, null, null);
    }

    private Filters mode(ModePreference mode) {
        return new Filters(subjectId, gradeId, mode, new Area(AreaScope.DISTRICTS, List.of(homeDistrictId), null),
                null, null, null, null, null);
    }

    private Filters area(AreaScope scope, List<UUID> districts, UUID province) {
        return new Filters(subjectId, gradeId, null, new Area(scope, districts, province), null, null, null, null, null);
    }

    private UUID user(UUID districtId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status, first_name, last_name, district_id)
                VALUES (?, ?, ?, 'x', 'ACTIVE', 'An', 'Nguyễn', ?)
                """, id, "cid-" + id, id + "@example.com", districtId);
        return id;
    }

    private UUID tutor() {
        return tutor("VERIFIED", "ACTIVE", "70");
    }

    private UUID tutor(String profileStatus, String accountStatus, String score) {
        UUID userId = user(null);
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", accountStatus, userId);
        boolean verified = profileStatus.equals("VERIFIED");
        return insert("""
                INSERT INTO tutors (id, user_id, profile_status, profile_reviewed_by, profile_reviewed_at,
                                    recommendation_score)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, profileStatus, verified ? reviewerId : null,
                verified ? Timestamp.valueOf(LocalDateTime.now()) : null, new BigDecimal(score));
    }

    private UUID posting(UUID tutorId, Posting posting) {
        UUID id = insert("""
                INSERT INTO teaching_requests
                    (id, tutor_id, subject_id, title, teaching_mode, status, expected_price, quantity)
                VALUES (?, ?, ?, 'Dạy kèm', ?, ?, ?, ?)
                """, tutorId, posting.subjectId == null ? subjectId : posting.subjectId, posting.mode, posting.status,
                posting.price, posting.quantity);
        if (posting.cosine != null) {
            jdbc.update("UPDATE teaching_requests SET embedding = CAST(? AS vector) WHERE id = ?",
                    literal(unit(posting.cosine)), id);
        }
        for (UUID grade : posting.gradeIds == null ? List.of(gradeId) : posting.gradeIds) {
            insert("INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES (?, ?, ?)", grade, id);
        }
        for (UUID district : posting.districtIds) {
            insert("INSERT INTO teaching_request_districts (id, teaching_request_id, district_id) VALUES (?, ?, ?)",
                    id, district);
        }
        for (Object[] slot : posting.slots) {
            insert("""
                    INSERT INTO teaching_request_availabilities (id, teaching_request_id, day_of_week, start_time, end_time)
                    VALUES (?, ?, ?, CAST(? AS time), CAST(? AS time))
                    """, id, slot[0], slot[1], slot[2]);
        }
        return id;
    }

    /** An application of the test student to the posting, decided {@code daysAgo} days ago. */
    private UUID application(UUID teachingRequestId, String status, int daysAgo) {
        Timestamp when = Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo));
        return insert("""
                INSERT INTO student_tutor_requests
                    (id, student_id, grade_id, teaching_request_id, learning_mode, status, created_at, updated_at,
                     responded_at)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?)
                """, studentId, gradeId, teachingRequestId, status, when, when,
                status.equals("REJECTED") || status.equals("ACCEPTED") ? when : null);
    }

    private static final String DECISIONS = """
            SELECT count(*) FROM student_tutor_requests
            WHERE deleted_at IS NULL AND status %s
              AND COALESCE(responded_at, updated_at) > now() - INTERVAL '180 days'
            """;

    private UUID otherStudent() {
        return insert("INSERT INTO students (id, user_id) VALUES (?, ?)", user(null));
    }

    /** An application of any student, decided (or sent) {@code daysAgo} days ago. */
    private UUID applicationOf(UUID student, UUID teachingRequestId, String status, int daysAgo, String price) {
        Timestamp when = Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo));
        return insert("""
                INSERT INTO student_tutor_requests
                    (id, student_id, grade_id, teaching_request_id, learning_mode, status, created_at, updated_at,
                     responded_at, proposed_price)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?, ?)
                """, student, gradeId, teachingRequestId, status, when, when,
                status.equals("PENDING") ? null : when, price == null ? null : new BigDecimal(price));
    }

    private UUID tag(String group, String code, UUID parentId) {
        // Fixed codes such as "trung_binh" may already be in the vocabulary of this database.
        List<UUID> existing = jdbc.queryForList(
                "SELECT id FROM recommendation_tags WHERE tag_group = ? AND code = ? AND deleted_at IS NULL",
                UUID.class, group, code);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return insert("INSERT INTO recommendation_tags (id, tag_group, code, name, parent_id) VALUES (?, ?, ?, ?, ?)",
                group, code, "Tên " + code, parentId);
    }

    private void entityTag(String entityType, UUID entityId, UUID tagId, String role) {
        insert("""
                INSERT INTO recommendation_entity_tags (id, entity_type, entity_id, tag_id, role, source)
                VALUES (?, ?, ?, ?, ?, 'USER')
                """, entityType, entityId, tagId, role);
    }

    private UUID insert(String sql, Object... args) {
        UUID id = UUID.randomUUID();
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        jdbc.update(sql, all);
        return id;
    }

    /** A unit vector whose cosine with the query vector (1, 0, 0, ...) is exactly {@code cosine}. */
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

    private JsonView log(UUID logId) {
        entityManager.flush();
        entityManager.clear();
        return new JsonView(logRepository.findById(logId).orElseThrow().getReturnedCandidateIds());
    }

    private record JsonView(tools.jackson.databind.JsonNode candidates) {

        private tools.jackson.databind.JsonNode of(UUID candidateId) {
            for (tools.jackson.databind.JsonNode node : candidates) {
                if (node.path("candidateId").asString().equals(candidateId.toString())) {
                    return node;
                }
            }
            throw new AssertionError("Candidate " + candidateId + " is not in the log");
        }

        double number(UUID candidateId, String name) {
            return of(candidateId).path(name).asDouble();
        }

        double willingness(UUID candidateId, String name) {
            return of(candidateId).path("willingness").path(name).asDouble();
        }

        double g(UUID candidateId) {
            return of(candidateId).path("g").asDouble();
        }

        double feature(UUID candidateId, String name) {
            return of(candidateId).path("features").path(name).asDouble();
        }

        boolean isNull(UUID candidateId, String name) {
            return of(candidateId).path("features").path(name).isNull();
        }
    }

    private final class Posting {
        private UUID subjectId;
        private List<UUID> gradeIds;
        private String mode = "ONLINE";
        private String status = "OPEN";
        private BigDecimal price = new BigDecimal("150000");
        private Double cosine = 0.6;
        private Integer quantity = 1;
        private List<UUID> districtIds = List.of();
        private final List<Object[]> slots = new java.util.ArrayList<>();

        Posting subject(UUID value) {
            subjectId = value;
            return this;
        }

        Posting grades(UUID... values) {
            gradeIds = List.of(values);
            return this;
        }

        Posting mode(String value) {
            mode = value;
            return this;
        }

        Posting status(String value) {
            status = value;
            return this;
        }

        Posting price(String value) {
            price = value == null ? null : new BigDecimal(value);
            return this;
        }

        Posting quantity(Integer value) {
            quantity = value;
            return this;
        }

        Posting cosine(Double value) {
            cosine = value;
            return this;
        }

        Posting districts(UUID... values) {
            districtIds = List.of(values);
            return this;
        }

        Posting slot(int day, String start, String end) {
            slots.add(new Object[] { day, start, end });
            return this;
        }
    }
}
