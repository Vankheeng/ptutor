package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;

public interface AiEnrichmentJobRepository extends JpaRepository<AiEnrichmentJob, UUID> {

    // Building blocks of the backfill statements; the entity type is a literal in each of them.
    String BACKFILL_INSERT_A = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'EMBED', '""";
    String BACKFILL_INSERT_B = "', e.id, 'PENDING', 0, :now, :now, :now ";
    /** Skips entities that already have an open job, or a job newer than their last change. */
    String BACKFILL_GUARD_A = """
            AND NOT EXISTS (
                SELECT 1 FROM ai_enrichment_jobs j
                WHERE j.job_type = 'EMBED' AND j.entity_id = e.id AND j.entity_type = '""";
    String BACKFILL_GUARD_B = """
            ' AND (j.status IN ('PENDING', 'PROCESSING') OR j.updated_at >= e.updated_at))
            ON CONFLICT DO NOTHING
            """;

    /**
     * Queues a job, or re-arms the open one for the same entity. Re-arming a PROCESSING job makes
     * the worker run it again, so a change made while the job was being processed is not lost.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            VALUES (gen_random_uuid(), :jobType, :entityType, :entityId, 'PENDING', 0, :now, :now, :now)
            ON CONFLICT (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING')
            DO UPDATE SET status = 'PENDING', attempts = 0, next_attempt_at = :now,
                          last_error = NULL, updated_at = :now
            """, nativeQuery = true)
    int enqueue(
            @Param("jobType") String jobType,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("now") LocalDateTime now);

    /** Postings whose text includes the tutor profile. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'EMBED', 'TEACHING_REQUEST', tr.id, 'PENDING', 0, :now, :now, :now
            FROM teaching_requests tr
            WHERE tr.tutor_id = :tutorId AND tr.deleted_at IS NULL
              AND tr.status IN ('OPEN', 'MATCHED')
            ON CONFLICT (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING')
            DO UPDATE SET status = 'PENDING', attempts = 0, next_attempt_at = :now,
                          last_error = NULL, updated_at = :now
            """, nativeQuery = true)
    int enqueueTeachingRequestsOfTutor(@Param("tutorId") UUID tutorId, @Param("now") LocalDateTime now);

    /** Postings whose text includes the student profile. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'EMBED', 'STUDYING_REQUEST', sr.id, 'PENDING', 0, :now, :now, :now
            FROM studying_requests sr
            WHERE sr.student_id = :studentId AND sr.deleted_at IS NULL
              AND sr.status IN ('OPEN', 'MATCHED')
            ON CONFLICT (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING')
            DO UPDATE SET status = 'PENDING', attempts = 0, next_attempt_at = :now,
                          last_error = NULL, updated_at = :now
            """, nativeQuery = true)
    int enqueueStudyingRequestsOfStudent(@Param("studentId") UUID studentId, @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT * FROM ai_enrichment_jobs
            WHERE job_type = :jobType AND status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AiEnrichmentJob> lockDueJobs(
            @Param("jobType") String jobType, @Param("now") LocalDateTime now, @Param("limit") int limit);

    /** The last successful run for an entity; its hash tells whether the text changed since. */
    Optional<AiEnrichmentJob> findFirstByJobTypeAndEntityTypeAndEntityIdAndStatusOrderByUpdatedAtDesc(
            AiJobType jobType, RecommendationEntityType entityType, UUID entityId, AiJobStatus status);

    boolean existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
            AiJobType jobType, AiJobStatus status, LocalDateTime now);

    /** Closes a job only if nobody re-armed it while it was being processed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiEnrichmentJob job
               set job.status = :status, job.contentHash = :contentHash, job.lastError = :lastError,
                   job.processedAt = :now, job.updatedAt = :now
             where job.id = :jobId and job.status = com.ptutor.backend.entity.enums.AiJobStatus.PROCESSING
            """)
    int finish(
            @Param("jobId") UUID jobId,
            @Param("status") AiJobStatus status,
            @Param("contentHash") String contentHash,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiEnrichmentJob job
               set job.status = com.ptutor.backend.entity.enums.AiJobStatus.PENDING,
                   job.nextAttemptAt = :nextAttemptAt, job.lastError = :lastError, job.updatedAt = :now
             where job.id = :jobId and job.status = com.ptutor.backend.entity.enums.AiJobStatus.PROCESSING
            """)
    int retryLater(
            @Param("jobId") UUID jobId,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    /** A worker died mid-job: give its jobs back to the queue. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiEnrichmentJob job
               set job.status = com.ptutor.backend.entity.enums.AiJobStatus.PENDING,
                   job.nextAttemptAt = :now, job.updatedAt = :now
             where job.status = com.ptutor.backend.entity.enums.AiJobStatus.PROCESSING
               and job.updatedAt < :staleBefore
            """)
    int releaseStale(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    /**
     * Finished jobs are only kept for a while; FAILED ones stay for diagnosis. The latest successful
     * ENRICH job of each entity is kept for good: its hash is what prevents paying for the LLM again
     * when the text has not changed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AiEnrichmentJob job
             where job.status in (com.ptutor.backend.entity.enums.AiJobStatus.DONE,
                                  com.ptutor.backend.entity.enums.AiJobStatus.SKIPPED)
               and job.updatedAt < :before
               and (job.jobType <> com.ptutor.backend.entity.enums.AiJobType.ENRICH
                    or job.status = com.ptutor.backend.entity.enums.AiJobStatus.SKIPPED
                    or exists (select 1 from AiEnrichmentJob newer
                                where newer.jobType = com.ptutor.backend.entity.enums.AiJobType.ENRICH
                                  and newer.entityType = job.entityType
                                  and newer.entityId = job.entityId
                                  and newer.status = com.ptutor.backend.entity.enums.AiJobStatus.DONE
                                  and newer.updatedAt > job.updatedAt))
            """)
    int deleteFinishedBefore(@Param("before") LocalDateTime before);

    @Query("select job.status as status, count(job) as total from AiEnrichmentJob job group by job.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        AiJobStatus getStatus();

        long getTotal();
    }

    @Modifying(flushAutomatically = true)
    @Query(value = BACKFILL_INSERT_A + "TEACHING_REQUEST" + BACKFILL_INSERT_B + """
            FROM teaching_requests e
            WHERE e.deleted_at IS NULL AND e.embedding IS NULL
              AND e.status IN ('OPEN', 'MATCHED')
            """ + BACKFILL_GUARD_A + "TEACHING_REQUEST" + BACKFILL_GUARD_B, nativeQuery = true)
    int backfillTeachingRequests(@Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = BACKFILL_INSERT_A + "STUDYING_REQUEST" + BACKFILL_INSERT_B + """
            FROM studying_requests e
            WHERE e.deleted_at IS NULL AND e.embedding IS NULL
              AND e.status IN ('OPEN', 'MATCHED')
            """ + BACKFILL_GUARD_A + "STUDYING_REQUEST" + BACKFILL_GUARD_B, nativeQuery = true)
    int backfillStudyingRequests(@Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = BACKFILL_INSERT_A + "TUTOR" + BACKFILL_INSERT_B + """
            FROM tutors e
            WHERE e.deleted_at IS NULL AND e.profile_embedding IS NULL
            """ + BACKFILL_GUARD_A + "TUTOR" + BACKFILL_GUARD_B, nativeQuery = true)
    int backfillTutors(@Param("now") LocalDateTime now);

    /** Live postings of one subject, queued again after its topic vocabulary changed. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'ENRICH', e.entity_type, e.id, 'PENDING', 0, :now, :now, :now
            FROM (
                SELECT 'TEACHING_REQUEST' AS entity_type, id FROM teaching_requests
                 WHERE deleted_at IS NULL AND status IN ('OPEN', 'MATCHED') AND subject_id = :subjectId
                UNION ALL
                SELECT 'STUDYING_REQUEST', id FROM studying_requests
                 WHERE deleted_at IS NULL AND status IN ('OPEN', 'MATCHED') AND subject_id = :subjectId
            ) e
            ON CONFLICT (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING')
            DO UPDATE SET status = 'PENDING', attempts = 0, next_attempt_at = :now,
                          last_error = NULL, updated_at = :now
            """, nativeQuery = true)
    int enqueueEnrichmentForSubject(@Param("subjectId") UUID subjectId, @Param("now") LocalDateTime now);

    /** Entities that were never enriched (rows older than the feature, or whose job was lost). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai_enrichment_jobs
                (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
            SELECT gen_random_uuid(), 'ENRICH', e.entity_type, e.id, 'PENDING', 0, :now, :now, :now
            FROM (
                SELECT 'TEACHING_REQUEST' AS entity_type, id FROM teaching_requests
                 WHERE deleted_at IS NULL AND status IN ('OPEN', 'MATCHED')
                UNION ALL
                SELECT 'STUDYING_REQUEST', id FROM studying_requests
                 WHERE deleted_at IS NULL AND status IN ('OPEN', 'MATCHED')
                UNION ALL
                SELECT 'TUTOR', id FROM tutors WHERE deleted_at IS NULL
                UNION ALL
                SELECT 'STUDENT', id FROM students WHERE deleted_at IS NULL
            ) e
            WHERE NOT EXISTS (
                SELECT 1 FROM ai_enrichment_jobs j
                WHERE j.job_type = 'ENRICH' AND j.entity_type = e.entity_type AND j.entity_id = e.id
                  AND (j.status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')
                       OR COALESCE(j.last_error, '') <> 'Posting is not published'))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int backfillEnrichment(@Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = BACKFILL_INSERT_A + "STUDENT" + BACKFILL_INSERT_B + """
            FROM students e
            WHERE e.deleted_at IS NULL AND e.profile_embedding IS NULL
            """ + BACKFILL_GUARD_A + "STUDENT" + BACKFILL_GUARD_B, nativeQuery = true)
    int backfillStudents(@Param("now") LocalDateTime now);
}
