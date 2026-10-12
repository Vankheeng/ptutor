package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EnrichRequest;
import com.ptutor.backend.client.AiServiceClient.EnrichResult;
import com.ptutor.backend.client.AiServiceClient.ExtractedSlot;
import com.ptutor.backend.client.AiServiceClient.ExtractedTag;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagRole;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.EnrichmentResultRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

import jakarta.persistence.EntityManager;

/**
 * ENRICH jobs against PostgreSQL with the LLM replaced by a mock. Uses the vocabulary seeded at
 * start-up plus one topic of a subject created for the test. Everything is rolled back.
 */
@SpringBootTest
@Transactional
class EnrichmentJobIntegrationTest {

    @Autowired AiJobQueueService queueService;
    @Autowired AiEnrichmentJobRepository jobRepository;
    @Autowired RecommendationTagRepository tagRepository;
    @Autowired RecommendationEntityTagRepository entityTagRepository;
    @Autowired EnrichmentResultRepository resultRepository;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired RecommendationTagSeeder seeder;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private AiServiceClient aiServiceClient;
    private EnrichmentJobProcessor processor;
    private UUID subjectId;
    private UUID gradeId;
    private UUID tutorId;
    private UUID studentId;
    private UUID teachingRequestId;
    private UUID studyingRequestId;
    private String topicCode;
    private String otherSubjectTopicCode;

    @BeforeEach
    void setUp() {
        aiServiceClient = mock(AiServiceClient.class);
        processor = new EnrichmentJobProcessor(jobRepository, tagRepository, resultRepository, queueService,
                textBuilder, aiServiceClient, entityManager, transactionManager, 25_000, 3, 0.6);
        // Jobs committed by a developer's running application must not leak into the assertions.
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán-" + UUID.randomUUID());
        UUID otherSubjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')",
                "Văn-" + UUID.randomUUID());
        gradeId = insert("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')", "Lớp 9-" + UUID.randomUUID());
        topicCode = "chu_de_" + UUID.randomUUID().toString().substring(0, 8);
        otherSubjectTopicCode = "chu_de_khac_" + UUID.randomUUID().toString().substring(0, 8);
        insert("INSERT INTO recommendation_tags (id, tag_group, subject_id, code, name) VALUES (?, 'TOPIC', ?, ?, 'Chủ đề thử')",
                subjectId, topicCode);
        insert("INSERT INTO recommendation_tags (id, tag_group, subject_id, code, name) VALUES (?, 'TOPIC', ?, ?, 'Chủ đề môn khác')",
                otherSubjectId, otherSubjectTopicCode);
        tutorId = insert("""
                INSERT INTO tutors (id, user_id, teaching_methodology, target_student_type)
                VALUES (?, ?, 'Dạy chậm và chắc, gọi 0912345678', 'Học sinh trung bình')
                """, insertUser());
        studentId = insert("INSERT INTO students (id, user_id, weak_points) VALUES (?, ?, 'Yếu hình học')", insertUser());
        teachingRequestId = insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status, preferred_schedule)
                VALUES (?, ?, ?, 'Luyện thi vào 10', 'ONLINE', 'OPEN', 'Tối thứ 2 và thứ 4')
                """, tutorId, subjectId);
        insert("INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id) VALUES (?, ?, ?)", gradeId,
                teachingRequestId);
        studyingRequestId = insert("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, title, learning_mode, status, quantity, preferred_schedule)
                VALUES (?, ?, ?, ?, 'Tìm gia sư Toán 9', 'ONLINE', 'OPEN', 1, 'Tối thứ 3')
                """, studentId, subjectId, gradeId);
    }

    @Test
    void sendsOnlyScrubbedFieldsAndTheVocabularyOfThePostingsSubject() {
        answer(List.of(), List.of());

        process(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        process(RecommendationEntityType.TUTOR, tutorId);

        ArgumentCaptor<EnrichRequest> sent = ArgumentCaptor.forClass(EnrichRequest.class);
        verify(aiServiceClient, times(2)).enrich(sent.capture(), any());
        EnrichRequest posting = sent.getAllValues().get(0);
        assertThat(posting.entityType()).isEqualTo("TEACHING_REQUEST");
        assertThat(posting.subject()).startsWith("Toán-");
        assertThat(posting.grades()).singleElement().asString().startsWith("Lớp 9-");
        assertThat(posting.fields()).containsOnlyKeys("title", "preferred_schedule");
        assertThat(posting.extract()).containsExactly("schedule", "tags");
        assertThat(codes(posting, "TOPIC")).containsExactly(topicCode);
        assertThat(codes(posting, "STYLE")).contains("kien_nhan", "cham_chac");
        assertThat(codes(posting, "SEGMENT")).contains("mat_goc", "trung_binh", "kha", "gioi");
        assertThat(codes(posting, "GOAL")).contains("thi_vao_10");
        EnrichRequest profile = sent.getAllValues().get(1);
        assertThat(profile.subject()).isNull();
        assertThat(profile.extract()).containsExactly("tags");
        assertThat(profile.fields().get("teaching_methodology")).isEqualTo("Dạy chậm và chắc, gọi");
        assertThat(codes(profile, "TOPIC")).as("a profile may mention any subject")
                .contains(topicCode, otherSubjectTopicCode);
    }

    @Test
    void postingWithAProposedSubjectIsNotSentToTheLlmUntilAnAdministratorHasSetItsSubject() {
        UUID proposed = insert("""
                INSERT INTO teaching_requests (id, tutor_id, custom_subject_name, title, teaching_mode, status)
                VALUES (?, ?, 'Cờ vua, zalo 0912345678', 'Dạy cờ vua thiếu nhi', 'ONLINE', 'PENDING_REVIEW')
                """, tutorId);
        answer(List.of(), List.of(new ExtractedTag("STYLE", "kien_nhan", 0.9)));

        Outcome whileWaiting = process(RecommendationEntityType.TEACHING_REQUEST, proposed);

        assertThat(whileWaiting).isEqualTo(Outcome.SKIPPED);
        verify(aiServiceClient, never()).enrich(any(), any());
        assertThat(tags("TEACHING_REQUEST", proposed)).isEmpty();
        assertThat(jobs()).filteredOn(job -> job.get("job_type").equals("EMBED"))
                .as("the vector is still computed while the posting waits for review").hasSize(1);

        // The reviewer links the posting to a catalogue subject; approval queues the job again.
        jdbc.update("UPDATE teaching_requests SET subject_id = ?, custom_subject_name = NULL, status = 'OPEN' WHERE id = ?",
                subjectId, proposed);
        entityManager.clear();
        Outcome afterApproval = process(RecommendationEntityType.TEACHING_REQUEST, proposed);

        ArgumentCaptor<EnrichRequest> sent = ArgumentCaptor.forClass(EnrichRequest.class);
        verify(aiServiceClient, times(1)).enrich(sent.capture(), any());
        assertThat(afterApproval).isEqualTo(Outcome.DONE);
        assertThat(sent.getValue().subject()).startsWith("Toán-");
        assertThat(codes(sent.getValue(), "TOPIC")).containsExactly(topicCode);
        assertThat(tags("TEACHING_REQUEST", proposed)).containsExactly("STYLE:kien_nhan:OFFERED:0.90:LLM");
    }

    @Test
    void storesValidatedTagsWithRolesDerivedFromWhoTheTextIsAbout() {
        answer(List.of(), List.of(
                new ExtractedTag("STYLE", "cham_chac", 0.9),
                new ExtractedTag("SEGMENT", "trung_binh", 0.8),
                new ExtractedTag("GOAL", "thi_vao_10", 0.95),
                new ExtractedTag("TOPIC", topicCode, 1.7),
                new ExtractedTag("STYLE", "kien_nhan", 0.59),
                new ExtractedTag("STYLE", "ma_khong_ton_tai", 0.99),
                new ExtractedTag("GOAL", "cham_chac", 0.99),
                new ExtractedTag("TOPIC", otherSubjectTopicCode, 0.99),
                new ExtractedTag("STYLE", "cham_chac", 0.7)));

        Outcome outcome = process(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);

        assertThat(outcome).isEqualTo(Outcome.DONE);
        assertThat(tags("TEACHING_REQUEST", teachingRequestId)).containsExactlyInAnyOrder(
                "STYLE:cham_chac:OFFERED:0.90:LLM", "SEGMENT:trung_binh:TARGET:0.80:LLM",
                "GOAL:thi_vao_10:STRENGTH:0.95:LLM", "TOPIC:" + topicCode + ":STRENGTH:1.00:LLM");
    }

    @ParameterizedTest
    @CsvSource({
        "STUDENT, TOPIC, NEED", "STUDENT, GOAL, SELF", "STUDENT, STYLE, PREFERRED", "STUDENT, SEGMENT, SELF",
        "STUDYING_REQUEST, TOPIC, NEED", "STUDYING_REQUEST, STYLE, PREFERRED",
        "TUTOR, TOPIC, STRENGTH", "TUTOR, GOAL, STRENGTH", "TUTOR, STYLE, OFFERED", "TUTOR, SEGMENT, TARGET",
        "TEACHING_REQUEST, GOAL, STRENGTH", "TEACHING_REQUEST, SEGMENT, TARGET"
    })
    void roleFollowsFromEntityAndTagGroup(
            RecommendationEntityType entityType, RecommendationTagGroup group, RecommendationTagRole expected) {
        assertThat(EnrichmentJobProcessor.role(entityType, group)).isEqualTo(expected);
    }

    @Test
    void storesInferredAvailabilityOnlyWithinSaneHoursAndMarksItAsInferred() {
        answer(List.of(
                new ExtractedSlot(1, "19:00", "21:00", 0.9),
                new ExtractedSlot(3, "19:00", "21:00", 0.9),
                new ExtractedSlot(3, "19:00", "21:00", 0.8),
                new ExtractedSlot(5, "19:00", "21:00", 0.5),
                new ExtractedSlot(6, "05:00", "07:00", 0.9),
                new ExtractedSlot(6, "21:00", "23:00", 0.9),
                new ExtractedSlot(7, "21:00", "19:00", 0.9),
                new ExtractedSlot(8, "19:00", "21:00", 0.9),
                new ExtractedSlot(2, "tối", "khuya", 0.9)), List.of());

        process(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);

        assertThat(slots("teaching_request_availabilities", "teaching_request_id", teachingRequestId))
                .containsExactly("1 19:00:00-21:00:00 LLM", "3 19:00:00-21:00:00 LLM");
    }

    @Test
    void neverInfersAvailabilityNextToSlotsTheUserEntered() {
        insert("""
                INSERT INTO studying_request_availabilities (id, studying_request_id, day_of_week, start_time, end_time)
                VALUES (?, ?, 6, TIME '08:00', TIME '10:00')
                """, studyingRequestId);
        answer(List.of(new ExtractedSlot(2, "19:00", "21:00", 0.9)), List.of());

        process(RecommendationEntityType.STUDYING_REQUEST, studyingRequestId);

        ArgumentCaptor<EnrichRequest> sent = ArgumentCaptor.forClass(EnrichRequest.class);
        verify(aiServiceClient).enrich(sent.capture(), any());
        assertThat(sent.getValue().extract()).containsExactly("tags");
        assertThat(slots("studying_request_availabilities", "studying_request_id", studyingRequestId))
                .containsExactly("6 08:00:00-10:00:00 USER");
    }

    @Test
    void skipsWithoutCallingTheLlmWhenTheTextDidNotChange() {
        answer(List.of(), List.of(new ExtractedTag("STYLE", "cham_chac", 0.9)));
        process(RecommendationEntityType.TUTOR, tutorId);

        Outcome again = process(RecommendationEntityType.TUTOR, tutorId);

        assertThat(again).isEqualTo(Outcome.SKIPPED);
        verify(aiServiceClient, times(1)).enrich(any(), any());
        assertThat(tags("TUTOR", tutorId)).containsExactly("STYLE:cham_chac:OFFERED:0.90:LLM");
    }

    @Test
    void replacesInferredTagsWhenTheTextChangesAndKeepsTagsChosenByTheUser() {
        UUID userChosen = jdbc.queryForObject(
                "SELECT id FROM recommendation_tags WHERE tag_group = 'STYLE' AND code = 'vui_ve'", UUID.class);
        insert("""
                INSERT INTO recommendation_entity_tags (id, entity_type, entity_id, tag_id, role, source)
                VALUES (?, 'TUTOR', ?, ?, 'OFFERED', 'USER')
                """, tutorId, userChosen);
        answer(List.of(), List.of(new ExtractedTag("STYLE", "cham_chac", 0.9), new ExtractedTag("STYLE", "vui_ve", 0.9)));
        process(RecommendationEntityType.TUTOR, tutorId);

        jdbc.update("UPDATE tutors SET teaching_methodology = 'Nghiêm khắc, luyện đề nhiều' WHERE id = ?", tutorId);
        entityManager.clear();
        answer(List.of(), List.of(new ExtractedTag("STYLE", "nghiem_khac", 0.9)));
        Outcome outcome = process(RecommendationEntityType.TUTOR, tutorId);

        assertThat(outcome).isEqualTo(Outcome.DONE);
        assertThat(tags("TUTOR", tutorId)).containsExactlyInAnyOrder(
                "STYLE:vui_ve:OFFERED:1.00:USER", "STYLE:nghiem_khac:OFFERED:0.90:LLM");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM recommendation_entity_tags
                WHERE entity_id = ? AND source = 'LLM' AND deleted_at IS NOT NULL
                """, Integer.class, tutorId)).as("old inferred tags are soft-deleted").isEqualTo(1);
    }

    @Test
    void clearsInferredTagsWithoutCallingTheLlmWhenTheTextIsRemoved() {
        answer(List.of(), List.of(new ExtractedTag("STYLE", "cham_chac", 0.9)));
        process(RecommendationEntityType.TUTOR, tutorId);

        jdbc.update("UPDATE tutors SET teaching_methodology = NULL, target_student_type = '  ' WHERE id = ?", tutorId);
        entityManager.clear();
        Outcome outcome = process(RecommendationEntityType.TUTOR, tutorId);

        assertThat(outcome).isEqualTo(Outcome.DONE);
        verify(aiServiceClient, times(1)).enrich(any(), any());
        assertThat(tags("TUTOR", tutorId)).isEmpty();
    }

    @Test
    void queuesAnEmbeddingRefreshWhoseTextContainsTheTagNames() {
        answer(List.of(), List.of(new ExtractedTag("GOAL", "thi_vao_10", 0.95)));

        process(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);

        assertThat(jobs()).extracting(job -> job.get("job_type") + ":" + job.get("status"))
                .containsExactlyInAnyOrder("ENRICH:DONE", "EMBED:PENDING");
        entityManager.clear();
        String goalName = jdbc.queryForObject(
                "SELECT name FROM recommendation_tags WHERE tag_group = 'GOAL' AND code = 'thi_vao_10'", String.class);
        AiServiceClient embedder = mock(AiServiceClient.class);
        when(embedder.embed(any(), any())).thenReturn(Optional.empty());
        EmbeddingJobProcessor embedding = new EmbeddingJobProcessor(jobRepository, textBuilder, entityTagRepository,
                embedder, entityManager, transactionManager, 5_000, 3);
        embedding.claim(5).forEach(embedding::process);
        ArgumentCaptor<List<String>> texts = ArgumentCaptor.captor();
        verify(embedder).embed(texts.capture(), any());
        assertThat(texts.getValue().get(0)).contains("Luyện thi vào 10").endsWith("Đã chuẩn hoá: " + goalName);
    }

    @Test
    void retriesThenFailsWhenTheLlmGivesNoAnswerAndWritesNothing() {
        when(aiServiceClient.enrich(any(), any())).thenReturn(Optional.empty());
        queueService.on(new com.ptutor.backend.event.EmbeddingRefreshRequestedEvent(
                RecommendationEntityType.TUTOR, tutorId));

        Outcome first = processor.process(claimOne());
        makeJobsDue();
        Outcome second = processor.process(claimOne());
        makeJobsDue();
        Outcome third = processor.process(claimOne());

        assertThat(List.of(first, second, third)).containsExactly(Outcome.RETRY, Outcome.RETRY, Outcome.FAILED);
        assertThat(tags("TUTOR", tutorId)).isEmpty();
        assertThat(jobs()).filteredOn(job -> job.get("job_type").equals("EMBED"))
                .as("embedding jobs (profile and its posting) were queued with the enrichment job, not after it")
                .extracting(job -> job.get("entity_id") + ":" + job.get("status"))
                .containsExactlyInAnyOrder(tutorId + ":PENDING", teachingRequestId + ":PENDING");
    }

    @Test
    void draftPostingIsNotSentToTheLlmUntilItIsLiveAndBackfillPicksItUpAfterwards() {
        jdbc.update("UPDATE studying_requests SET status = 'DRAFT' WHERE id = ?", studyingRequestId);
        answer(List.of(), List.of(new ExtractedTag("GOAL", "thi_vao_10", 0.9)));

        Outcome draft = process(RecommendationEntityType.STUDYING_REQUEST, studyingRequestId);

        assertThat(draft).isEqualTo(Outcome.SKIPPED);
        verify(aiServiceClient, never()).enrich(any(), any());

        // Even if the event at payment time were lost, the backfill queues the posting once it is live.
        jdbc.update("UPDATE studying_requests SET status = 'OPEN' WHERE id = ?", studyingRequestId);
        entityManager.clear();
        jobRepository.backfillEnrichment(LocalDateTime.now());
        Outcome live = processor.process(claimOnly(studyingRequestId));

        assertThat(live).isEqualTo(Outcome.DONE);
        assertThat(tags("STUDYING_REQUEST", studyingRequestId)).containsExactly("GOAL:thi_vao_10:SELF:0.90:LLM");
    }

    @Test
    void skipsEntityThatNoLongerExists() {
        Outcome outcome = process(RecommendationEntityType.STUDENT, UUID.randomUUID());

        assertThat(outcome).isEqualTo(Outcome.SKIPPED);
        verify(aiServiceClient, never()).enrich(any(), any());
    }

    @Test
    void cleanupKeepsTheLatestSuccessfulEnrichmentOfEachEntity() {
        Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
        Timestamp older = Timestamp.valueOf(LocalDateTime.now().minusDays(40));
        UUID latest = job("ENRICH", "DONE", tutorId, old);
        job("ENRICH", "DONE", tutorId, older);
        job("ENRICH", "SKIPPED", tutorId, old);
        job("EMBED", "DONE", tutorId, old);

        int deleted = jobRepository.deleteFinishedBefore(LocalDateTime.now().minusDays(7));

        assertThat(deleted).isEqualTo(3);
        assertThat(jobs()).singleElement().satisfies(job -> assertThat(job.get("id")).isEqualTo(latest));
    }

    @Test
    void backfillQueuesEntitiesNeverEnrichedOnlyOnce() {
        int first = jobRepository.backfillEnrichment(LocalDateTime.now());
        int second = jobRepository.backfillEnrichment(LocalDateTime.now());

        assertThat(first).isGreaterThanOrEqualTo(4);
        assertThat(second).isZero();
        assertThat(jobs()).filteredOn(job -> job.get("job_type").equals("ENRICH"))
                .extracting(job -> job.get("entity_id"))
                .contains(tutorId, studentId, teachingRequestId, studyingRequestId);
    }

    @Test
    void seedingTheVocabularyTwiceAddsNothingAndLinksTopicsToTheirParent() throws Exception {
        long before = tagRepository.count();

        seeder.run(new DefaultApplicationArguments());
        entityManager.flush();

        assertThat(tagRepository.count()).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                SELECT parent.code FROM recommendation_tags child
                JOIN recommendation_tags parent ON parent.id = child.parent_id
                WHERE child.tag_group = 'TOPIC' AND child.code = 'chung_minh'
                """, String.class)).isEqualTo("hinh_hoc_phang");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM recommendation_tags
                WHERE tag_group IN ('STYLE', 'SEGMENT', 'GOAL') AND deleted_at IS NULL
                """, Integer.class)).isGreaterThanOrEqualTo(23);
    }

    // ---------------------------------------------------------------- helpers

    private void answer(List<ExtractedSlot> schedule, List<ExtractedTag> tags) {
        when(aiServiceClient.enrich(any(), any())).thenReturn(Optional.of(new EnrichResult("gpt-6-luna", schedule, tags)));
    }

    /** Queues an ENRICH job for the entity the way saving it does, then runs it. */
    private Outcome process(RecommendationEntityType type, UUID id) {
        queueService.on(new com.ptutor.backend.event.EmbeddingRefreshRequestedEvent(type, id));
        return processor.process(claimOne());
    }

    private ClaimedJob claimOne() {
        entityManager.flush();
        List<ClaimedJob> claimed = processor.claim(10);
        assertThat(claimed).hasSize(1);
        return claimed.get(0);
    }

    /** Claims everything that is due and returns the job of the given entity. */
    private ClaimedJob claimOnly(UUID entityId) {
        entityManager.flush();
        return processor.claim(1_000).stream()
                .filter(job -> job.entityId().equals(entityId)).findFirst().orElseThrow();
    }

    private void makeJobsDue() {
        entityManager.flush();
        jdbc.update("UPDATE ai_enrichment_jobs SET next_attempt_at = ? WHERE status = 'PENDING' AND job_type = 'ENRICH'",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));
        entityManager.clear();
    }

    private List<String> codes(EnrichRequest request, String group) {
        return request.vocabulary().getOrDefault(group, List.of()).stream().map(VocabularyTag::code).toList();
    }

    private List<String> tags(String entityType, UUID entityId) {
        entityManager.flush();
        return jdbc.queryForList("""
                SELECT tag.tag_group || ':' || tag.code || ':' || et.role || ':' || et.confidence || ':' || et.source
                FROM recommendation_entity_tags et JOIN recommendation_tags tag ON tag.id = et.tag_id
                WHERE et.entity_type = ? AND et.entity_id = ? AND et.deleted_at IS NULL
                """, String.class, entityType, entityId);
    }

    private List<String> slots(String table, String column, UUID postingId) {
        entityManager.flush();
        return jdbc.queryForList("SELECT day_of_week || ' ' || start_time || '-' || end_time || ' ' || source FROM "
                + table + " WHERE " + column + " = ? AND deleted_at IS NULL ORDER BY day_of_week", String.class, postingId);
    }

    private List<Map<String, Object>> jobs() {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM ai_enrichment_jobs ORDER BY created_at, job_type");
    }

    private UUID job(String type, String status, UUID entityId, Timestamp updatedAt) {
        return insert("""
                INSERT INTO ai_enrichment_jobs
                    (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, 'TUTOR', ?, ?, 0, ?, ?, ?)
                """, type, entityId, status, updatedAt, updatedAt, updatedAt);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
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
