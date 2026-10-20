package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.RecommendationClickLog;
import com.ptutor.backend.entity.RecommendationEntityTag;
import com.ptutor.backend.entity.RecommendationExposureStats;
import com.ptutor.backend.entity.RecommendationExposureStatsId;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.RecommendationMatchAlert;
import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.RecommendationRerankCacheId;
import com.ptutor.backend.entity.RecommendationSavedSearch;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.RecommendationUserProfile;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.TeachingRequestAvailability;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDataSource;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationMatchSourceType;
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagRole;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Round-trips every table added or changed by V32 through its entity against PostgreSQL.
 * Everything happens inside the test transaction and is rolled back.
 */
@SpringBootTest
@Transactional
class RecommendationSchemaIntegrationTest {

    private static final int DIMENSIONS = 1024;

    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TutorRepository tutorRepository;
    @Autowired TeachingRequestRepository teachingRequestRepository;
    @Autowired RecommendationTagRepository tagRepository;
    @Autowired RecommendationEntityTagRepository entityTagRepository;
    @Autowired AiEnrichmentJobRepository jobRepository;
    @Autowired RecommendationRerankCacheRepository rerankCacheRepository;
    @Autowired RecommendationSavedSearchRepository savedSearchRepository;
    @Autowired RecommendationMatchAlertRepository matchAlertRepository;
    @Autowired RecommendationUserProfileRepository userProfileRepository;
    @Autowired RecommendationExposureStatsRepository exposureStatsRepository;
    @Autowired RecommendationLogRepository logRepository;
    @Autowired RecommendationClickLogRepository clickLogRepository;

    private UUID studentUserId;
    private UUID studentId;
    private UUID tutorId;
    private UUID teachingRequestId;

    @BeforeEach
    void setUp() {
        studentUserId = insertUser();
        studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO students (id, user_id) VALUES (?, ?)", studentId, studentUserId);
        tutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", tutorId, insertUser());
        teachingRequestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status)
                VALUES (?, ?, 'ONLINE', 'OPEN')
                """, teachingRequestId, tutorId);
    }

    @Test
    void storesProfileAndPostingEmbeddingsWith1024Dimensions() {
        Tutor tutor = tutorRepository.findById(tutorId).orElseThrow();
        tutor.setProfileEmbedding(vector(0.25f));
        tutor.setEmbeddingContentHash("a".repeat(64));
        Student student = studentRepository.findById(studentId).orElseThrow();
        student.setProfileEmbedding(vector(0.5f));
        student.setEmbeddingContentHash("b".repeat(64));
        TeachingRequest teachingRequest = teachingRequestRepository.findById(teachingRequestId).orElseThrow();
        teachingRequest.setEmbedding(vector(0.75f));
        teachingRequest.setEmbeddingSourceText("Toán 9, luyện thi vào 10");
        teachingRequest.setEmbeddingContentHash("c".repeat(64));
        teachingRequest.setEmbeddingModelVersion("bge-m3");
        flushAndClear();

        assertThat(tutorRepository.findById(tutorId).orElseThrow().getProfileEmbedding())
                .hasSize(DIMENSIONS).containsOnly(0.25f);
        assertThat(studentRepository.findById(studentId).orElseThrow().getProfileEmbedding())
                .hasSize(DIMENSIONS).containsOnly(0.5f);
        TeachingRequest reloaded = teachingRequestRepository.findById(teachingRequestId).orElseThrow();
        assertThat(reloaded.getEmbedding()).hasSize(DIMENSIONS).containsOnly(0.75f);
        assertThat(reloaded.getEmbeddingSourceText()).isEqualTo("Toán 9, luyện thi vào 10");
        assertThat(jdbc.queryForObject(
                "SELECT embedding <=> embedding FROM teaching_requests WHERE id = ?", Double.class,
                teachingRequestId)).isZero();
    }

    @Test
    void rejectsEmbeddingWithOldDimension() {
        Tutor tutor = tutorRepository.findById(tutorId).orElseThrow();
        tutor.setProfileEmbedding(new float[1536]);

        assertThatThrownBy(this::flushAndClear).hasMessageContaining("expected 1024 dimensions");
    }

    @Test
    void availabilitySourceDefaultsToUser() {
        TeachingRequest teachingRequest = teachingRequestRepository.findById(teachingRequestId).orElseThrow();
        teachingRequest.getAvailabilities().add(TeachingRequestAvailability.builder()
                .teachingRequest(teachingRequest).dayOfWeek(1)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build());
        teachingRequest.getAvailabilities().add(TeachingRequestAvailability.builder()
                .teachingRequest(teachingRequest).dayOfWeek(3)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0))
                .source(RecommendationDataSource.LLM).build());
        flushAndClear();

        assertThat(teachingRequestRepository.findById(teachingRequestId).orElseThrow().getAvailabilities())
                .extracting(TeachingRequestAvailability::getSource)
                .containsExactly(RecommendationDataSource.USER, RecommendationDataSource.LLM);
    }

    @Test
    void storesTagVocabularyAndEntityTags() {
        RecommendationTag parent = tagRepository.save(RecommendationTag.builder()
                .tagGroup(RecommendationTagGroup.STYLE).code("thu_kien_nhan").name("Kiên nhẫn").build());
        RecommendationTag child = tagRepository.save(RecommendationTag.builder()
                .tagGroup(RecommendationTagGroup.STYLE).parent(parent).code("thu_kien_nhan_voi_hs_yeu")
                .name("Kiên nhẫn với học sinh yếu").build());
        entityTagRepository.save(RecommendationEntityTag.builder()
                .entityType(RecommendationEntityType.TUTOR).entityId(tutorId).tag(child)
                .role(RecommendationTagRole.SELF).confidence(new BigDecimal("0.80"))
                .source(RecommendationDataSource.LLM).build());
        flushAndClear();

        RecommendationTag reloaded = tagRepository.findById(child.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RecommendationTagStatus.ACTIVE);
        assertThat(reloaded.getParent().getCode()).isEqualTo("thu_kien_nhan");
        assertThat(entityTagRepository.findAll())
                .filteredOn(tag -> tag.getEntityId().equals(tutorId))
                .singleElement()
                .satisfies(tag -> assertThat(tag.getConfidence()).isEqualByComparingTo("0.80"));
    }

    @Test
    void rejectsDuplicateTagCodeInSameGroup() {
        tagRepository.saveAndFlush(RecommendationTag.builder()
                .tagGroup(RecommendationTagGroup.GOAL).code("thu_thi_vao_10").name("Thi vào 10").build());

        assertThatThrownBy(() -> tagRepository.saveAndFlush(RecommendationTag.builder()
                .tagGroup(RecommendationTagGroup.GOAL).code("thu_thi_vao_10").name("Trùng").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesJobAndAllowsOnlyOneOpenJobPerEntity() {
        AiEnrichmentJob job = jobRepository.saveAndFlush(AiEnrichmentJob.builder()
                .jobType(AiJobType.EMBED).entityType(RecommendationEntityType.TEACHING_REQUEST)
                .entityId(teachingRequestId).contentHash("d".repeat(64)).build());

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isNotNull();
        assertThat(job.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> jobRepository.saveAndFlush(AiEnrichmentJob.builder()
                .jobType(AiJobType.EMBED).entityType(RecommendationEntityType.TEACHING_REQUEST)
                .entityId(teachingRequestId).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesRerankCacheUserProfileAndExposureStats() {
        RecommendationRerankCacheId cacheId = new RecommendationRerankCacheId(
                "e".repeat(64), RecommendationCandidateType.TEACHING_REQUEST, teachingRequestId, "bge-reranker-v2-m3");
        rerankCacheRepository.save(RecommendationRerankCache.builder()
                .id(cacheId).candidateContentHash("f".repeat(64)).rerankScore(new BigDecimal("0.8531")).build());
        ObjectNode affinity = JsonMapper.builder().build().createObjectNode().put("kien_nhan", 0.3);
        userProfileRepository.save(RecommendationUserProfile.builder()
                .userId(studentUserId).role(RecommendationProfileRole.STUDENT)
                .preferenceEmbedding(vector(0.1f)).tagAffinity(affinity).build());
        RecommendationExposureStatsId statsId = new RecommendationExposureStatsId(
                RecommendationCandidateType.TEACHING_REQUEST, teachingRequestId);
        exposureStatsRepository.save(RecommendationExposureStats.builder().id(statsId).impressions7d(4).build());
        flushAndClear();

        RecommendationRerankCache cache = rerankCacheRepository.findById(cacheId).orElseThrow();
        assertThat(cache.getRerankScore()).isEqualByComparingTo("0.8531");
        assertThat(cache.getComputedAt()).isNotNull();
        RecommendationUserProfile profile = userProfileRepository.findById(studentUserId).orElseThrow();
        assertThat(profile.getPreferenceEmbedding()).hasSize(DIMENSIONS);
        assertThat(profile.getTagAffinity().get("kien_nhan").asDouble()).isEqualTo(0.3);
        assertThat(profile.getSignalCount()).isZero();
        RecommendationExposureStats stats = exposureStatsRepository.findById(statsId).orElseThrow();
        assertThat(stats.getImpressions7d()).isEqualTo(4);
        assertThat(stats.getClicksTotal()).isZero();
    }

    @Test
    void storesSavedSearchLogClickAndMatchAlert() {
        User studentUser = userRepository.findById(studentUserId).orElseThrow();
        Student student = studentRepository.findById(studentId).orElseThrow();
        Tutor tutor = tutorRepository.findById(tutorId).orElseThrow();
        TeachingRequest teachingRequest = teachingRequestRepository.findById(teachingRequestId).orElseThrow();
        ObjectNode queryContext = JsonMapper.builder().build().createObjectNode().put("teachingMode", "ONLINE");

        RecommendationSavedSearch savedSearch = savedSearchRepository.save(RecommendationSavedSearch.builder()
                .user(studentUser).direction(RecommendationDirection.STUDENT_TO_TUTOR)
                .name("Toán 9 online").queryContext(queryContext).build());
        RecommendationLog log = logRepository.save(RecommendationLog.builder()
                .searcherStudent(student).queryContext(queryContext)
                .direction(RecommendationDirection.STUDENT_TO_TUTOR).algorithmVersion("V2-a")
                .queryText("cần gia sư toán 9").parsedQuery(queryContext)
                .savedSearch(savedSearch).latencyMs(420).build());
        // The existing XOR check still requires exactly one of clicked_student_id / clicked_tutor_id.
        RecommendationClickLog click = clickLogRepository.save(RecommendationClickLog.builder()
                .recommendationLog(log).clickedTutor(tutor).candidateTeachingRequest(teachingRequest)
                .rank(1).action(RecommendationAction.FAVORITED).build());
        RecommendationMatchAlert alert = matchAlertRepository.save(RecommendationMatchAlert.builder()
                .recipientUser(studentUser).sourceType(RecommendationMatchSourceType.SAVED_SEARCH)
                .sourceId(savedSearch.getId()).candidateType(RecommendationCandidateType.TEACHING_REQUEST)
                .candidateId(teachingRequestId).finalScore(new BigDecimal("0.7421")).candidateRank(1).build());
        flushAndClear();

        RecommendationSavedSearch reloadedSearch = savedSearchRepository.findById(savedSearch.getId()).orElseThrow();
        assertThat(reloadedSearch.getNotifyEnabled()).isTrue();
        assertThat(reloadedSearch.getQueryContext().get("teachingMode").asString()).isEqualTo("ONLINE");
        RecommendationLog reloadedLog = logRepository.findById(log.getId()).orElseThrow();
        assertThat(reloadedLog.getDirection()).isEqualTo(RecommendationDirection.STUDENT_TO_TUTOR);
        assertThat(reloadedLog.getSavedSearch().getId()).isEqualTo(savedSearch.getId());
        assertThat(reloadedLog.getLatencyMs()).isEqualTo(420);
        RecommendationClickLog reloadedClick = clickLogRepository.findById(click.getId()).orElseThrow();
        assertThat(reloadedClick.getAction()).isEqualTo(RecommendationAction.FAVORITED);
        assertThat(reloadedClick.getCandidateTeachingRequest().getId()).isEqualTo(teachingRequestId);
        RecommendationMatchAlert reloadedAlert = matchAlertRepository.findById(alert.getId()).orElseThrow();
        assertThat(reloadedAlert.getFinalScore()).isEqualByComparingTo("0.7421");
        assertThat(reloadedAlert.getCreatedAt()).isNotNull();
    }

    @Test
    void acceptsNewNotificationEventType() {
        UUID notificationId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO notifications (id, user_id, type, title, content, record_type, event_type)
                VALUES (?, ?, 'SYSTEM', 'Có gia sư phù hợp', 'x', 'SYSTEM_EVENT', 'RECOMMENDATION_MATCH_FOUND')
                """, notificationId, studentUserId);

        assertThat(jdbc.queryForObject(
                "SELECT event_type FROM notifications WHERE id = ?", String.class, notificationId))
                .isEqualTo("RECOMMENDATION_MATCH_FOUND");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private float[] vector(float value) {
        float[] vector = new float[DIMENSIONS];
        java.util.Arrays.fill(vector, value);
        return vector;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
                """, id, "cid-" + id, id + "@example.com");
        return id;
    }
}
