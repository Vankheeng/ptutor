package com.ptutor.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EnrichRequest;
import com.ptutor.backend.client.AiServiceClient.EnrichResult;
import com.ptutor.backend.client.AiServiceClient.ExtractedSlot;
import com.ptutor.backend.client.AiServiceClient.ExtractedTag;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.entity.AiEnrichmentJob;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.AiJobStatus;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagRole;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.EnrichmentResultRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

import jakarta.persistence.EntityManager;

/**
 * Runs ENRICH jobs (design 04): sends the free text of a profile or posting to the LLM, checks
 * every item it returns against the vocabulary and the rules, and stores tags and inferred
 * availability. The LLM is never trusted and never overwrites what a user entered.
 */
@Service
public class EnrichmentJobProcessor {

    static final LocalTime EARLIEST = LocalTime.of(6, 0);
    static final LocalTime LATEST = LocalTime.of(22, 30);

    private static final Logger log = LoggerFactory.getLogger(EnrichmentJobProcessor.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiEnrichmentJobRepository jobRepository;
    private final RecommendationTagRepository tagRepository;
    private final EnrichmentResultRepository resultRepository;
    private final AiJobQueueService queueService;
    private final EmbeddingTextBuilder textBuilder;
    private final AiServiceClient aiServiceClient;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;
    private final Duration timeout;
    private final int maxAttempts;
    private final double minConfidence;

    public EnrichmentJobProcessor(
            AiEnrichmentJobRepository jobRepository,
            RecommendationTagRepository tagRepository,
            EnrichmentResultRepository resultRepository,
            AiJobQueueService queueService,
            EmbeddingTextBuilder textBuilder,
            AiServiceClient aiServiceClient,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            @Value("${app.recommendation.enrichment.timeout-ms:25000}") long timeoutMs,
            @Value("${app.recommendation.enrichment.max-attempts:5}") int maxAttempts,
            @Value("${app.recommendation.enrichment.min-confidence:0.6}") double minConfidence) {
        this.jobRepository = jobRepository;
        this.tagRepository = tagRepository;
        this.resultRepository = resultRepository;
        this.queueService = queueService;
        this.textBuilder = textBuilder;
        this.aiServiceClient = aiServiceClient;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = maxAttempts;
        this.minConfidence = minConfidence;
    }

    public boolean hasDueJobs() {
        return jobRepository.existsByJobTypeAndStatusAndNextAttemptAtLessThanEqual(
                AiJobType.ENRICH, AiJobStatus.PENDING, LocalDateTime.now());
    }

    /** Marks up to {@code limit} due jobs as PROCESSING and returns them. */
    public List<ClaimedJob> claim(int limit) {
        return transaction.execute(status -> {
            List<AiEnrichmentJob> jobs = jobRepository.lockDueJobs(
                    AiJobType.ENRICH.name(), LocalDateTime.now(), limit);
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
            if (input.hash().equals(input.previousHash())) {
                return finish(job, AiJobStatus.SKIPPED, input.hash(), null);
            }
            EnrichResult result;
            if (input.request().fields().isEmpty()) {
                // Nothing to read: no call, but tags inferred from an earlier text are still cleared below.
                result = new EnrichResult("none", List.of(), List.of());
            } else {
                Optional<EnrichResult> answer = aiServiceClient.enrich(input.request(), timeout);
                if (answer.isEmpty()) {
                    return retryOrFail(job, "AI service did not return an extraction");
                }
                result = answer.get();
            }
            transaction.executeWithoutResult(status -> {
                store(job, input, result);
                jobRepository.finish(job.id(), AiJobStatus.DONE, input.hash(), null, LocalDateTime.now());
                // The embedded text contains the names of the tags, so it has to be refreshed.
                queueService.enqueueEmbedding(job.entityType(), job.entityId());
            });
            return Outcome.DONE;
        } catch (Exception exception) {
            log.warn("Enrichment job {} for {} {} failed", job.id(), job.entityType(), job.entityId(), exception);
            return retryOrFail(job, exception.toString());
        }
    }

    private Optional<Prepared> prepare(ClaimedJob job) {
        Map<String, String> fields = new LinkedHashMap<>();
        Subject subject = null;
        RequestStatus postingStatus = null;
        List<String> grades = new ArrayList<>();
        boolean posting = false;
        switch (job.entityType()) {
            case STUDENT -> {
                Student student = entityManager.find(Student.class, job.entityId());
                if (student == null) {
                    return Optional.empty();
                }
                put(fields, "learning_style", student.getLearningStyle());
                put(fields, "personality_tags", student.getPersonalityTags());
                put(fields, "goals_description", student.getGoalsDescription());
                put(fields, "current_level", student.getCurrentLevel());
                put(fields, "weak_points", student.getWeakPoints());
                put(fields, "introduction", student.getIntroduction());
            }
            case TUTOR -> {
                Tutor tutor = entityManager.find(Tutor.class, job.entityId());
                if (tutor == null) {
                    return Optional.empty();
                }
                put(fields, "teaching_style_tags", tutor.getTeachingStyleTags());
                put(fields, "teaching_methodology", tutor.getTeachingMethodology());
                put(fields, "strength_subjects", tutor.getStrengthSubjects());
                put(fields, "target_student_type", tutor.getTargetStudentType());
                put(fields, "introduction", tutor.getIntroduction());
                put(fields, "education", tutor.getEducation());
            }
            case STUDYING_REQUEST -> {
                StudyingRequest request = entityManager.find(StudyingRequest.class, job.entityId());
                if (request == null) {
                    return Optional.empty();
                }
                posting = true;
                postingStatus = request.getStatus();
                subject = request.getSubject();
                if (request.getGrade() != null) {
                    grades.add(request.getGrade().getName());
                }
                put(fields, "title", request.getTitle());
                put(fields, "description", request.getDescription());
                put(fields, "note", request.getNote());
                put(fields, "learning_goals", request.getLearningGoals());
                put(fields, "preferred_schedule", request.getPreferredSchedule());
            }
            case TEACHING_REQUEST -> {
                TeachingRequest request = entityManager.find(TeachingRequest.class, job.entityId());
                if (request == null) {
                    return Optional.empty();
                }
                posting = true;
                postingStatus = request.getStatus();
                subject = request.getSubject();
                request.getGradeAssociations().stream()
                        .filter(association -> association.getGrade() != null)
                        .forEach(association -> grades.add(association.getGrade().getName()));
                put(fields, "title", request.getTitle());
                put(fields, "description", request.getDescription());
                put(fields, "note", request.getNote());
                put(fields, "preferred_schedule", request.getPreferredSchedule());
            }
            default -> {
                return Optional.empty();
            }
        }

        if (posting && !EmbeddingJobProcessor.isPublished(postingStatus)) {
            // Drafts may never be paid for and postings awaiting review may change or be rejected: the LLM
            // is only paid for postings that are live. Becoming OPEN queues a new job.
            return Optional.of(Prepared.skip(EmbeddingJobProcessor.NOT_PUBLISHED));
        }
        if (posting && subject == null) {
            // Cannot happen for a live posting (review sets the subject); kept as a safeguard.
            return Optional.of(Prepared.skip("Subject not reviewed yet"));
        }

        // Postings only see the topics of their subject; profiles may mention any subject.
        UUID subjectId = subject == null ? null : subject.getId();
        boolean anySubject = !posting;
        Map<String, List<VocabularyTag>> vocabulary = new LinkedHashMap<>();
        Map<String, RecommendationTag> allowed = new HashMap<>();
        for (RecommendationTag tag : tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(
                RecommendationTagStatus.ACTIVE)) {
            if (tag.getTagGroup() == RecommendationTagGroup.TOPIC && !anySubject
                    && (tag.getSubject() == null || !tag.getSubject().getId().equals(subjectId))) {
                continue;
            }
            String key = key(tag.getTagGroup().name(), tag.getCode());
            if (allowed.putIfAbsent(key, tag) == null) {
                vocabulary.computeIfAbsent(tag.getTagGroup().name(), group -> new ArrayList<>())
                        .add(new VocabularyTag(tag.getCode(), tag.getName()));
            }
        }

        // Availability is inferred only for postings, and never next to slots the user entered.
        boolean inferSchedule = posting && fields.containsKey("preferred_schedule")
                && !resultRepository.hasUserAvailability(job.entityType(), job.entityId());
        List<String> extract = inferSchedule ? List.of("schedule", "tags") : List.of("tags");
        String subjectName = subject == null ? null : subject.getName();
        EnrichRequest request = new EnrichRequest(
                job.entityType().name(), subjectName, List.copyOf(grades), Map.copyOf(fields), vocabulary, extract);
        // For a posting the offered topics are part of the hash, so adding or switching off a topic of its
        // subject runs it again. Profiles see every topic and are deliberately left out of that.
        List<String> topicCodes = !posting ? List.of() : vocabulary
                .getOrDefault(RecommendationTagGroup.TOPIC.name(), List.of()).stream()
                .map(VocabularyTag::code).sorted().toList();
        String hash = textBuilder.hash(job.entityType() + "|" + subjectName + "|" + grades + "|" + fields
                + "|" + extract + "|" + topicCodes);
        String previousHash = jobRepository
                .findFirstByJobTypeAndEntityTypeAndEntityIdAndStatusOrderByUpdatedAtDesc(
                        AiJobType.ENRICH, job.entityType(), job.entityId(), AiJobStatus.DONE)
                .map(AiEnrichmentJob::getContentHash)
                .orElse(null);
        return Optional.of(new Prepared(request, hash, previousHash, allowed, posting, inferSchedule, null));
    }

    private void store(ClaimedJob job, Prepared input, EnrichResult result) {
        LocalDateTime now = LocalDateTime.now();
        resultRepository.softDeleteInferredTags(job.entityType(), job.entityId(), now);
        Set<String> written = new LinkedHashSet<>();
        for (ExtractedTag tag : result.tags()) {
            RecommendationTag known = input.allowed().get(key(tag.group(), tag.code()));
            if (known == null || tag.confidence() < minConfidence) {
                continue;
            }
            RecommendationTagRole role = role(job.entityType(), known.getTagGroup());
            if (written.add(known.getId() + ":" + role)) {
                resultRepository.insertInferredTag(
                        job.entityType(), job.entityId(), known.getId(), role, Math.min(1.0, tag.confidence()), now);
            }
        }
        if (!input.posting()) {
            return;
        }
        // Inferred slots are replaced, or removed when the user has meanwhile entered their own.
        resultRepository.softDeleteInferredAvailabilities(job.entityType(), job.entityId(), now);
        if (!input.inferSchedule()) {
            return;
        }
        Set<String> slots = new LinkedHashSet<>();
        for (ExtractedSlot slot : result.schedule()) {
            LocalTime start = time(slot.start());
            LocalTime end = time(slot.end());
            if (slot.confidence() < minConfidence || slot.dayOfWeek() < 1 || slot.dayOfWeek() > 7
                    || start == null || end == null || !start.isBefore(end)
                    || start.isBefore(EARLIEST) || end.isAfter(LATEST)) {
                continue;
            }
            if (slots.add(slot.dayOfWeek() + "|" + start + "|" + end)) {
                resultRepository.insertInferredAvailability(
                        job.entityType(), job.entityId(), slot.dayOfWeek(), start, end, now);
            }
        }
    }

    /** The role follows from who the text is about and the kind of tag; the LLM's own role is ignored. */
    static RecommendationTagRole role(RecommendationEntityType entityType, RecommendationTagGroup group) {
        boolean learner = entityType == RecommendationEntityType.STUDENT
                || entityType == RecommendationEntityType.STUDYING_REQUEST;
        return switch (group) {
            case TOPIC -> learner ? RecommendationTagRole.NEED : RecommendationTagRole.STRENGTH;
            case GOAL -> learner ? RecommendationTagRole.SELF : RecommendationTagRole.STRENGTH;
            case STYLE -> learner ? RecommendationTagRole.PREFERRED : RecommendationTagRole.OFFERED;
            case SEGMENT -> learner ? RecommendationTagRole.SELF : RecommendationTagRole.TARGET;
        };
    }

    private void put(Map<String, String> fields, String name, String value) {
        String scrubbed = textBuilder.scrub(value);
        if (!scrubbed.isEmpty()) {
            fields.put(name, scrubbed);
        }
    }

    private LocalTime time(String value) {
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String key(String group, String code) {
        return (group == null ? "" : group.strip().toUpperCase()) + ":" + (code == null ? "" : code.strip());
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
        Duration delay = EmbeddingJobProcessor.RETRY_DELAYS.get(
                Math.min(job.attempts(), EmbeddingJobProcessor.RETRY_DELAYS.size()) - 1);
        LocalDateTime now = LocalDateTime.now();
        transaction.executeWithoutResult(
                tx -> jobRepository.retryLater(job.id(), now.plus(delay), message, now));
        return Outcome.RETRY;
    }

    private record Prepared(
            EnrichRequest request, String hash, String previousHash, Map<String, RecommendationTag> allowed,
            boolean posting, boolean inferSchedule, String skipReason) {

        static Prepared skip(String reason) {
            return new Prepared(null, null, null, Map.of(), true, false, reason);
        }
    }
}
