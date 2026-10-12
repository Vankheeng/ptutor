package com.ptutor.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EmbeddingResult;
import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;

import jakarta.persistence.EntityManager;

/**
 * Runs EMBED jobs: builds the text of the entity, asks the AI service for its vector and stores
 * it. The HTTP call happens outside any database transaction.
 */
@Service
public class EmbeddingJobProcessor {

    /** Delay before the 2nd, 3rd, ... attempt; the last value repeats. */
    static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60));
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String NOT_PUBLISHED = "Posting is not published";

    private static final Logger log = LoggerFactory.getLogger(EmbeddingJobProcessor.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiEnrichmentJobRepository jobRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final AiServiceClient aiServiceClient;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;
    private final Duration embedTimeout;
    private final int maxAttempts;

    public EmbeddingJobProcessor(
            AiEnrichmentJobRepository jobRepository,
            EmbeddingTextBuilder textBuilder,
            RecommendationEntityTagRepository entityTagRepository,
            AiServiceClient aiServiceClient,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            @Value("${app.recommendation.embedding.timeout-ms:5000}") long embedTimeoutMs,
            @Value("${app.recommendation.embedding.max-attempts:5}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.textBuilder = textBuilder;
        this.entityTagRepository = entityTagRepository;
        this.aiServiceClient = aiServiceClient;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.embedTimeout = Duration.ofMillis(embedTimeoutMs);
        this.maxAttempts = maxAttempts;
    }

    public boolean hasDueJobs() {
        return jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                AiJobType.EMBED, AiJobStatus.PENDING, LocalDateTime.now());
    }

    public int releaseStaleJobs() {
        LocalDateTime now = LocalDateTime.now();
        return transaction.execute(status -> jobRepository.releaseStale(now.minus(STALE_AFTER), now));
    }

    /** Marks up to {@code limit} due jobs as PROCESSING and returns them. */
    public List<ClaimedJob> claim(int limit) {
        return transaction.execute(status -> {
            List<AiEnrichmentJob> jobs = jobRepository.lockDueJobs(
                    AiJobType.EMBED.name(), LocalDateTime.now(), limit);
            jobs.forEach(job -> {
                job.setStatus(AiJobStatus.PROCESSING);
                job.setAttempts(job.getAttempts() + 1);
            });
            return jobs.stream()
                    .map(job -> new ClaimedJob(job.getId(), job.getEntityType(), job.getEntityId(), job.getAttempts()))
                    .toList();
        });
    }

    public Outcome process(ClaimedJob job) {
        try {
            Optional<Prepared> prepared = readOnlyTransaction.execute(status -> prepare(job));
            if (prepared.isEmpty()) {
                return finish(job, AiJobStatus.SKIPPED, null, "Entity no longer exists");
            }
            Prepared input = prepared.get();
            if (input.skipReason() != null) {
                return finish(job, AiJobStatus.SKIPPED, null, input.skipReason());
            }
            if (input.text().isBlank()) {
                return finish(job, AiJobStatus.SKIPPED, null, "Nothing to embed");
            }
            if (input.upToDate()) {
                return finish(job, AiJobStatus.SKIPPED, input.hash(), null);
            }
            Optional<EmbeddingResult> result = aiServiceClient.embed(List.of(input.text()), embedTimeout);
            if (result.isEmpty()) {
                return retryOrFail(job, "AI service did not return an embedding");
            }
            transaction.executeWithoutResult(status -> {
                store(job, input, result.get());
                jobRepository.finish(job.id(), AiJobStatus.DONE, input.hash(), null, LocalDateTime.now());
            });
            return Outcome.DONE;
        } catch (Exception exception) {
            log.warn("Embedding job {} for {} {} failed", job.id(), job.entityType(), job.entityId(), exception);
            return retryOrFail(job, exception.toString());
        }
    }

    private Optional<Prepared> prepare(ClaimedJob job) {
        return switch (job.entityType()) {
            case TEACHING_REQUEST -> Optional.ofNullable(entityManager.find(TeachingRequest.class, job.entityId()))
                    .map(request -> !isPublished(request.getStatus()) ? Prepared.skip(NOT_PUBLISHED) : prepared(textBuilder.forTeachingRequest(request, tagNames(
                                    RecommendationEntityType.TEACHING_REQUEST, request.getId(),
                                    RecommendationEntityType.TUTOR,
                                    request.getTutor() == null ? null : request.getTutor().getId())),
                            request.getEmbeddingContentHash(), request.getEmbedding()));
            case STUDYING_REQUEST -> Optional.ofNullable(entityManager.find(StudyingRequest.class, job.entityId()))
                    .map(request -> !isPublished(request.getStatus()) ? Prepared.skip(NOT_PUBLISHED) : prepared(textBuilder.forStudyingRequest(request, tagNames(
                                    RecommendationEntityType.STUDYING_REQUEST, request.getId(),
                                    RecommendationEntityType.STUDENT,
                                    request.getStudent() == null ? null : request.getStudent().getId())),
                            request.getEmbeddingContentHash(), request.getEmbedding()));
            case TUTOR -> Optional.ofNullable(entityManager.find(Tutor.class, job.entityId()))
                    .map(tutor -> prepared(textBuilder.forTutor(tutor,
                                    tagNames(RecommendationEntityType.TUTOR, tutor.getId(), null, null)),
                            tutor.getEmbeddingContentHash(), tutor.getProfileEmbedding()));
            case STUDENT -> Optional.ofNullable(entityManager.find(Student.class, job.entityId()))
                    .map(student -> prepared(textBuilder.forStudent(student,
                                    tagNames(RecommendationEntityType.STUDENT, student.getId(), null, null)),
                            student.getEmbeddingContentHash(), student.getProfileEmbedding()));
            default -> Optional.empty();
        };
    }

    /** Names of the tags of an entity and, for a posting, of the person behind it. */
    private List<String> tagNames(
            RecommendationEntityType type, UUID id, RecommendationEntityType ownerType, UUID ownerId) {
        List<String> names = new java.util.ArrayList<>();
        entityTagRepository.findAllByEntityTypeAndEntityId(type, id)
                .forEach(tag -> names.add(tag.getTag().getName()));
        if (ownerId != null) {
            entityTagRepository.findAllByEntityTypeAndEntityId(ownerType, ownerId)
                    .forEach(tag -> names.add(tag.getTag().getName()));
        }
        return names;
    }

    private Prepared prepared(String text, String storedHash, float[] storedVector) {
        String hash = textBuilder.hash(text);
        return new Prepared(text, hash, storedVector != null && hash.equals(storedHash), null);
    }

    /**
     * Only postings that are live are worth the work: drafts may never be paid for and postings
     * awaiting review may still change or be rejected. Becoming OPEN queues a new job.
     */
    public static boolean isPublished(RequestStatus status) {
        return status == RequestStatus.OPEN || status == RequestStatus.MATCHED;
    }

    /** Updates only the embedding columns, so {@code updated_at} of the entity is left alone. */
    private void store(ClaimedJob job, Prepared input, EmbeddingResult result) {
        String entity;
        String vectorField;
        switch (job.entityType()) {
            case TEACHING_REQUEST -> {
                entity = "TeachingRequest";
                vectorField = "embedding";
            }
            case STUDYING_REQUEST -> {
                entity = "StudyingRequest";
                vectorField = "embedding";
            }
            case TUTOR -> {
                entity = "Tutor";
                vectorField = "profileEmbedding";
            }
            case STUDENT -> {
                entity = "Student";
                vectorField = "profileEmbedding";
            }
            default -> throw new IllegalStateException("Unsupported entity type " + job.entityType());
        }
        entityManager.createQuery("update " + entity + " e set e." + vectorField + " = :vector,"
                        + " e.embeddingSourceText = :text, e.embeddingContentHash = :hash,"
                        + " e.embeddingModelVersion = :model, e.embeddingUpdatedAt = :now where e.id = :id")
                .setParameter("vector", result.vectors().get(0))
                .setParameter("text", input.text())
                .setParameter("hash", input.hash())
                .setParameter("model", result.model())
                .setParameter("now", LocalDateTime.now())
                .setParameter("id", job.entityId())
                .executeUpdate();
    }

    private Outcome finish(ClaimedJob job, AiJobStatus status, String hash, String note) {
        transaction.executeWithoutResult(
                tx -> jobRepository.finish(job.id(), status, hash, note, LocalDateTime.now()));
        return status == AiJobStatus.SKIPPED ? Outcome.SKIPPED : Outcome.FAILED;
    }

    private Outcome retryOrFail(ClaimedJob job, String error) {
        String message = error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        if (job.attempts() >= maxAttempts) {
            return finish(job, AiJobStatus.FAILED, null, message);
        }
        Duration delay = RETRY_DELAYS.get(Math.min(job.attempts(), RETRY_DELAYS.size()) - 1);
        LocalDateTime now = LocalDateTime.now();
        transaction.executeWithoutResult(
                tx -> jobRepository.retryLater(job.id(), now.plus(delay), message, now));
        return Outcome.RETRY;
    }

    public enum Outcome { DONE, SKIPPED, RETRY, FAILED }

    public record ClaimedJob(UUID id, RecommendationEntityType entityType, UUID entityId, int attempts) {
    }

    private record Prepared(String text, String hash, boolean upToDate, String skipReason) {

        static Prepared skip(String reason) {
            return new Prepared("", null, false, reason);
        }
    }
}
