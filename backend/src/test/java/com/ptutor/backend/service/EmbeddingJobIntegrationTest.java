package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EmbeddingResult;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.event.EmbeddingRefreshRequestedEvent;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

import jakarta.persistence.EntityManager;

/**
 * Outbox and EMBED job processing against PostgreSQL, with the AI service replaced by a mock.
 * The processor's own transactions join the test transaction, so everything is rolled back.
 */
@SpringBootTest
@Transactional
class EmbeddingJobIntegrationTest {

    private static final String MODEL = "bge-m3-int8";

    @Autowired AiJobQueueService queueService;
    @Autowired AiEnrichmentJobRepository jobRepository;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired com.ptutor.backend.repository.RecommendationEntityTagRepository entityTagRepository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired JdbcTemplate jdbc;

    private AiServiceClient aiServiceClient;
    private EmbeddingJobProcessor processor;
    private UUID tutorId;
    private UUID studentId;
    private UUID teachingRequestId;
    private UUID studyingRequestId;

    @BeforeEach
    void setUp() {
        aiServiceClient = mock(AiServiceClient.class);
        processor = new EmbeddingJobProcessor(
                jobRepository, textBuilder, entityTagRepository, aiServiceClient, entityManager, transactionManager,
                5_000, 3);
        // Jobs committed by a developer's running application must not leak into the assertions.
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        UUID subjectId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", subjectId, "Toán-" + subjectId);
        jdbc.update("INSERT INTO grades (id, name, status) VALUES (?, ?, 'ACTIVE')", gradeId, "Lớp-" + gradeId);
        tutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id, introduction) VALUES (?, ?, 'Dạy Toán THCS 5 năm')",
                tutorId, insertUser());
        studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO students (id, user_id, weak_points) VALUES (?, ?, 'Hình học')",
                studentId, insertUser());
        teachingRequestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status, updated_at)
                VALUES (?, ?, ?, 'Luyện thi vào 10', 'ONLINE', 'OPEN', TIMESTAMP '2026-01-01 00:00:00')
                """, teachingRequestId, tutorId, subjectId);
        studyingRequestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO studying_requests
                    (id, student_id, subject_id, grade_id, title, learning_mode, status, quantity)
                VALUES (?, ?, ?, ?, 'Tìm gia sư Toán 9', 'ONLINE', 'OPEN', 1)
                """, studyingRequestId, studentId, subjectId, gradeId);
    }

    @Test
    void eventQueuesOneEmbeddingAndOneEnrichmentJobAndRepeatedChangesDoNotDuplicateThem() {
        eventPublisher.publishEvent(new EmbeddingRefreshRequestedEvent(
                RecommendationEntityType.TEACHING_REQUEST, teachingRequestId));
        eventPublisher.publishEvent(new EmbeddingRefreshRequestedEvent(
                RecommendationEntityType.TEACHING_REQUEST, teachingRequestId));

        assertThat(jobs()).extracting(job -> job.get("job_type")).containsExactlyInAnyOrder("EMBED", "ENRICH");
        assertThat(jobs()).filteredOn(job -> job.get("job_type").equals("EMBED")).singleElement().satisfies(job -> {
            assertThat(job.get("job_type")).isEqualTo("EMBED");
            assertThat(job.get("entity_type")).isEqualTo("TEACHING_REQUEST");
            assertThat(job.get("entity_id")).isEqualTo(teachingRequestId);
            assertThat(job.get("status")).isEqualTo("PENDING");
            assertThat(job.get("attempts")).isEqualTo(0);
        });
    }

    @Test
    void profileChangeAlsoQueuesTheOpenPostingsOfThatPerson() {
        UUID cancelledRequestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO teaching_requests (id, tutor_id, teaching_mode, status)
                VALUES (?, ?, 'ONLINE', 'CANCELLED')
                """, cancelledRequestId, tutorId);

        queueService.enqueueEmbedding(RecommendationEntityType.TUTOR, tutorId);
        queueService.enqueueEmbedding(RecommendationEntityType.STUDENT, studentId);

        assertThat(jobs()).extracting(job -> job.get("entity_type") + ":" + job.get("entity_id"))
                .containsExactlyInAnyOrder(
                        "TUTOR:" + tutorId, "TEACHING_REQUEST:" + teachingRequestId,
                        "STUDENT:" + studentId, "STUDYING_REQUEST:" + studyingRequestId);
    }

    @Test
    void storesVectorHashModelAndSourceTextWithoutTouchingUpdatedAt() {
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        when(aiServiceClient.embed(any(), any())).thenReturn(embedding(0.5f));

        ClaimedJob job = claimOne();
        Outcome outcome = processor.process(job);

        assertThat(job.attempts()).isEqualTo(1);
        assertThat(outcome).isEqualTo(Outcome.DONE);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT embedding IS NOT NULL AS has_vector, vector_dims(embedding) AS dims, embedding_source_text,
                       embedding_content_hash, embedding_model_version, embedding_updated_at, updated_at
                FROM teaching_requests WHERE id = ?
                """, teachingRequestId);
        String text = (String) row.get("embedding_source_text");
        assertThat(row.get("has_vector")).isEqualTo(true);
        assertThat(row.get("dims")).isEqualTo(1024);
        assertThat(text).contains("Luyện thi vào 10").contains("Giới thiệu: Dạy Toán THCS 5 năm");
        assertThat(row.get("embedding_content_hash")).isEqualTo(textBuilder.hash(text));
        assertThat(row.get("embedding_model_version")).isEqualTo(MODEL);
        assertThat(row.get("embedding_updated_at")).isNotNull();
        assertThat(((Timestamp) row.get("updated_at")).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        ArgumentCaptor<List<String>> sent = ArgumentCaptor.captor();
        verify(aiServiceClient).embed(sent.capture(), any(Duration.class));
        assertThat(sent.getValue()).containsExactly(text);
        assertThat(jobs()).singleElement().satisfies(done -> {
            assertThat(done.get("status")).isEqualTo("DONE");
            assertThat(done.get("content_hash")).isEqualTo(textBuilder.hash(text));
            assertThat(done.get("processed_at")).isNotNull();
        });
    }

    @Test
    void embedsProfilesAndStudyingRequestsToo() {
        queueService.enqueueEmbedding(RecommendationEntityType.TUTOR, tutorId);
        queueService.enqueueEmbedding(RecommendationEntityType.STUDENT, studentId);
        when(aiServiceClient.embed(any(), any())).thenReturn(embedding(0.25f));

        List<Outcome> outcomes = processor.claim(10).stream().map(processor::process).toList();

        assertThat(outcomes).hasSize(4).containsOnly(Outcome.DONE);
        assertThat(jdbc.queryForObject("SELECT embedding_source_text FROM tutors WHERE id = ?", String.class, tutorId))
                .isEqualTo("Giới thiệu: Dạy Toán THCS 5 năm");
        assertThat(jdbc.queryForObject(
                "SELECT vector_dims(profile_embedding) FROM students WHERE id = ?", Integer.class, studentId))
                .isEqualTo(1024);
        assertThat(jdbc.queryForObject(
                "SELECT embedding_source_text FROM studying_requests WHERE id = ?", String.class, studyingRequestId))
                .contains("Tìm gia sư Toán 9").contains("Điểm yếu: Hình học");
    }

    @Test
    void skipsWhenTextIsUnchangedAndReEmbedsWhenItChanges() {
        when(aiServiceClient.embed(any(), any())).thenReturn(embedding(0.5f));
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        processor.process(claimOne());

        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        Outcome unchanged = processor.process(claimOne());
        jdbc.update("UPDATE teaching_requests SET title = 'Luyện thi vào 10 chuyên' WHERE id = ?", teachingRequestId);
        entityManager.clear();
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        Outcome changed = processor.process(claimOne());

        assertThat(unchanged).isEqualTo(Outcome.SKIPPED);
        assertThat(changed).isEqualTo(Outcome.DONE);
        verify(aiServiceClient, org.mockito.Mockito.times(2)).embed(any(), any());
        assertThat(jdbc.queryForObject(
                "SELECT embedding_source_text FROM teaching_requests WHERE id = ?", String.class, teachingRequestId))
                .contains("Luyện thi vào 10 chuyên");
    }

    @Test
    void skipsEntityWithNothingToEmbedOrThatNoLongerExists() {
        UUID emptyTutorId = UUID.randomUUID();
        jdbc.update("INSERT INTO tutors (id, user_id) VALUES (?, ?)", emptyTutorId, insertUser());
        queueService.enqueueEmbedding(RecommendationEntityType.TUTOR, emptyTutorId);
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, UUID.randomUUID());

        List<Outcome> outcomes = processor.claim(10).stream().map(processor::process).toList();

        assertThat(outcomes).containsExactly(Outcome.SKIPPED, Outcome.SKIPPED);
        verify(aiServiceClient, never()).embed(any(), any());
        assertThat(jobs()).extracting(job -> job.get("status")).containsOnly("SKIPPED");
    }

    @Test
    void postingIsEmbeddedOnlyOnceItIsLive() {
        when(aiServiceClient.embed(any(), any())).thenReturn(embedding(0.5f));
        for (String status : List.of("DRAFT", "PENDING_REVIEW", "REJECTED", "CLOSED")) {
            jdbc.update("UPDATE teaching_requests SET status = ? WHERE id = ?", status, teachingRequestId);
            entityManager.clear();
            queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);

            assertThat(processor.process(claimOne())).as(status).isEqualTo(Outcome.SKIPPED);
        }
        verify(aiServiceClient, never()).embed(any(), any());
        assertThat(jobs()).extracting(job -> job.get("last_error")).containsOnly("Posting is not published");

        // Payment (or approval) makes it live and queues the job again.
        jdbc.update("UPDATE teaching_requests SET status = 'OPEN' WHERE id = ?", teachingRequestId);
        entityManager.clear();
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);

        assertThat(processor.process(claimOne())).isEqualTo(Outcome.DONE);
        assertThat(jdbc.queryForObject(
                "SELECT embedding IS NOT NULL FROM teaching_requests WHERE id = ?", Boolean.class, teachingRequestId))
                .isTrue();
    }

    @Test
    void backfillAndProfileChangesOnlyQueueLivePostings() {
        jdbc.update("UPDATE teaching_requests SET status = 'DRAFT', embedding = NULL WHERE id = ?", teachingRequestId);
        jdbc.update("UPDATE studying_requests SET status = 'DRAFT', embedding = NULL WHERE id = ?", studyingRequestId);

        jobRepository.backfillTeachingRequests(LocalDateTime.now());
        jobRepository.backfillStudyingRequests(LocalDateTime.now());
        jobRepository.backfillEnrichment(LocalDateTime.now());
        queueService.enqueueEmbedding(RecommendationEntityType.TUTOR, tutorId);
        queueService.enqueueEmbedding(RecommendationEntityType.STUDENT, studentId);

        assertThat(jobs()).extracting(job -> job.get("entity_id"))
                .doesNotContain(teachingRequestId, studyingRequestId)
                .contains(tutorId, studentId);
    }

    @Test
    void retriesWithBackoffThenFailsAfterMaxAttempts() {
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        when(aiServiceClient.embed(any(), any())).thenReturn(Optional.empty());

        Outcome first = processor.process(claimOne());
        Map<String, Object> afterFirst = jobs().get(0);
        assertThat(processor.claim(10)).as("not due again before the backoff elapses").isEmpty();
        makeJobsDue();
        Outcome second = processor.process(claimOne());
        LocalDateTime secondRetryAt = ((Timestamp) jobs().get(0).get("next_attempt_at")).toLocalDateTime();
        makeJobsDue();
        Outcome third = processor.process(claimOne());

        assertThat(List.of(first, second, third)).containsExactly(Outcome.RETRY, Outcome.RETRY, Outcome.FAILED);
        assertThat(afterFirst.get("status")).isEqualTo("PENDING");
        assertThat(afterFirst.get("attempts")).isEqualTo(1);
        assertThat((String) afterFirst.get("last_error")).contains("did not return an embedding");
        assertThat(((Timestamp) afterFirst.get("next_attempt_at")).toLocalDateTime())
                .isBetween(LocalDateTime.now().plusSeconds(50), LocalDateTime.now().plusSeconds(70));
        assertThat(secondRetryAt).isAfter(LocalDateTime.now().plusMinutes(4));
        assertThat(jobs()).singleElement().satisfies(job -> {
            assertThat(job.get("status")).isEqualTo("FAILED");
            assertThat(job.get("attempts")).isEqualTo(3);
        });
        assertThat(jdbc.queryForObject(
                "SELECT embedding IS NULL FROM teaching_requests WHERE id = ?", Boolean.class, teachingRequestId))
                .isTrue();
    }

    @Test
    void changeMadeWhileJobIsProcessingKeepsTheJobQueued() {
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        when(aiServiceClient.embed(any(), any())).thenReturn(embedding(0.5f));
        ClaimedJob job = claimOne();
        entityManager.flush();

        // The owner edits the posting after the worker claimed the job.
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        entityManager.clear();
        processor.process(job);

        assertThat(jobs()).singleElement().satisfies(rearmed -> {
            assertThat(rearmed.get("status")).isEqualTo("PENDING");
            assertThat(rearmed.get("attempts")).isEqualTo(0);
        });
        assertThat(processor.claim(10)).hasSize(1);
    }

    @Test
    void releasesJobsLeftProcessingByADeadWorker() {
        queueService.enqueueEmbedding(RecommendationEntityType.TEACHING_REQUEST, teachingRequestId);
        claimOne();
        entityManager.flush();
        assertThat(processor.releaseStaleJobs()).as("a fresh PROCESSING job is left alone").isZero();

        jdbc.update("UPDATE ai_enrichment_jobs SET updated_at = ?",
                Timestamp.valueOf(LocalDateTime.now().minus(EmbeddingJobProcessor.STALE_AFTER).minusMinutes(1)));
        entityManager.clear();

        assertThat(processor.releaseStaleJobs()).isEqualTo(1);
        assertThat(jobs()).singleElement().satisfies(job -> assertThat(job.get("status")).isEqualTo("PENDING"));
    }

    @Test
    void backfillQueuesEntitiesWithoutEmbeddingOnlyOnce() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE teaching_requests SET embedding = NULL WHERE id = ?", teachingRequestId);

        int first = jobRepository.backfillTeachingRequests(now);
        int second = jobRepository.backfillTeachingRequests(now.plusSeconds(1));

        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(second).isZero();
        assertThat(jobs()).filteredOn(job -> teachingRequestId.equals(job.get("entity_id"))).hasSize(1);
        assertThat(jobRepository.backfillStudyingRequests(now)).isGreaterThanOrEqualTo(1);
        assertThat(jobRepository.backfillTutors(now)).isGreaterThanOrEqualTo(1);
        assertThat(jobRepository.backfillStudents(now)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void cleanUpDeletesOnlyOldFinishedJobsAndCountsByStatus() {
        LocalDateTime old = LocalDateTime.now().minusDays(8);
        insertJob("DONE", old);
        insertJob("SKIPPED", old);
        insertJob("FAILED", old);
        insertJob("PENDING", old);
        insertJob("DONE", LocalDateTime.now().minusDays(6));

        int deleted = jobRepository.deleteFinishedBefore(LocalDateTime.now().minusDays(7));

        assertThat(deleted).isEqualTo(2);
        assertThat(jobRepository.countByStatus())
                .extracting(row -> row.getStatus() + "=" + row.getTotal())
                .containsExactlyInAnyOrder("DONE=1", "FAILED=1", "PENDING=1");
    }

    private void insertJob(String status, LocalDateTime updatedAt) {
        Timestamp time = Timestamp.valueOf(updatedAt);
        jdbc.update("""
                INSERT INTO ai_enrichment_jobs
                    (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
                VALUES (?, 'EMBED', 'TUTOR', ?, ?, 0, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), status, time, time, time);
    }

    private ClaimedJob claimOne() {
        List<ClaimedJob> claimed = processor.claim(10);
        assertThat(claimed).hasSize(1);
        return claimed.get(0);
    }

    private void makeJobsDue() {
        entityManager.flush();
        jdbc.update("UPDATE ai_enrichment_jobs SET next_attempt_at = ? WHERE status = 'PENDING'",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));
        entityManager.clear();
    }

    private List<Map<String, Object>> jobs() {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM ai_enrichment_jobs ORDER BY created_at, entity_type");
    }

    private Optional<EmbeddingResult> embedding(float value) {
        float[] vector = new float[AiServiceClient.EMBEDDING_DIMENSIONS];
        Arrays.fill(vector, value);
        return Optional.of(new EmbeddingResult(MODEL, List.of(vector)));
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
