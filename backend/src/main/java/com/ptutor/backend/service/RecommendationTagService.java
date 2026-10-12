package com.ptutor.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.SuggestTagsRequest;
import com.ptutor.backend.client.AiServiceClient.TagSuggestions;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.dto.request.RecommendationTagCreateRequest;
import com.ptutor.backend.dto.request.RecommendationTagUpdateRequest;
import com.ptutor.backend.dto.response.RecommendationTagResponse;
import com.ptutor.backend.dto.response.RecommendationTagSuggestionResponse;
import com.ptutor.backend.dto.response.RecommendationTagSuggestionResponse.Suggestion;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.SubjectRepository;

import jakarta.persistence.EntityManager;

/**
 * The tag vocabulary: what users can pick as filter chips and what the LLM may attach to profiles
 * and postings. Administrators type tags themselves or start from LLM suggestions; either way a
 * tag only exists once an administrator created it.
 */
@Service
public class RecommendationTagService {

    static final int SAMPLES_PER_SIDE = 10;
    static final int SAMPLE_MAX_CHARS = 300;
    static final int DEFAULT_SUGGESTIONS = 10;

    private final RecommendationTagRepository tagRepository;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final SubjectRepository subjectRepository;
    private final AiEnrichmentJobRepository jobRepository;
    private final AiJobQueueService queueService;
    private final AiServiceClient aiServiceClient;
    private final EmbeddingTextBuilder textBuilder;
    private final EntityManager entityManager;
    private final Duration suggestionTimeout;

    public RecommendationTagService(
            RecommendationTagRepository tagRepository,
            RecommendationEntityTagRepository entityTagRepository,
            SubjectRepository subjectRepository,
            AiEnrichmentJobRepository jobRepository,
            AiJobQueueService queueService,
            AiServiceClient aiServiceClient,
            EmbeddingTextBuilder textBuilder,
            EntityManager entityManager,
            @Value("${app.recommendation.tags.suggestion-timeout-ms:30000}") long suggestionTimeoutMs) {
        this.tagRepository = tagRepository;
        this.entityTagRepository = entityTagRepository;
        this.subjectRepository = subjectRepository;
        this.jobRepository = jobRepository;
        this.queueService = queueService;
        this.aiServiceClient = aiServiceClient;
        this.textBuilder = textBuilder;
        this.entityManager = entityManager;
        this.suggestionTimeout = Duration.ofMillis(suggestionTimeoutMs);
    }

    /** Active tags for the filter chips. TOPIC tags are returned only for the subject asked for. */
    @Transactional(readOnly = true)
    public List<RecommendationTagResponse> findSelectable(RecommendationTagGroup group, UUID subjectId) {
        return tagRepository.search(group, null, RecommendationTagStatus.ACTIVE).stream()
                .filter(tag -> tag.getTagGroup() != RecommendationTagGroup.TOPIC
                        || (subjectId != null && tag.getSubject() != null
                                && tag.getSubject().getId().equals(subjectId)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationTagResponse> findForAdmin(
            RecommendationTagGroup group, UUID subjectId, RecommendationTagStatus status) {
        return tagRepository.search(group, subjectId, status).stream().map(this::toResponse).toList();
    }

    /** All or nothing: one invalid item rejects the whole request. */
    @Transactional
    public List<RecommendationTagResponse> create(RecommendationTagCreateRequest request) {
        Map<String, RecommendationTag> createdByCode = new HashMap<>();
        List<RecommendationTag> created = new ArrayList<>();
        Set<UUID> subjectsWithNewTopics = new LinkedHashSet<>();
        for (RecommendationTagCreateRequest.Item item : request.tags()) {
            if (item.group() == RecommendationTagGroup.SEGMENT) {
                throw badRequest("SEGMENT_TAGS_ARE_FIXED",
                        "Level tags are part of the ranking formula and cannot be added");
            }
            Subject subject = subject(item.group(), item.subjectId());
            String code = item.code().strip();
            if (createdByCode.containsKey(code) || tagRepository.existsByCode(code)) {
                throw new ApiException(HttpStatus.CONFLICT, "TAG_CODE_ALREADY_EXISTS",
                        "Tag code already exists: " + code);
            }
            RecommendationTag parent = item.parentCode() == null || item.parentCode().isBlank() ? null
                    : parent(item.parentCode().strip(), item.group(), subject, createdByCode);
            RecommendationTag tag = tagRepository.save(RecommendationTag.builder()
                    .tagGroup(item.group()).subject(subject).parent(parent).code(code)
                    .name(item.name().strip()).description(normalize(item.description())).build());
            createdByCode.put(code, tag);
            created.add(tag);
            if (subject != null) {
                subjectsWithNewTopics.add(subject.getId());
            }
        }
        tagRepository.flush();
        subjectsWithNewTopics.forEach(this::enrichPostingsOfSubjectAgain);
        return created.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecommendationTagResponse update(UUID tagId, RecommendationTagUpdateRequest request) {
        RecommendationTag tag = find(tagId);
        String newName = request.name().strip();
        boolean renamed = !newName.equals(tag.getName());
        RecommendationTag parent = null;
        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            parent = parent(request.parentCode().strip(), tag.getTagGroup(), tag.getSubject(), Map.of());
            if (parent.getId().equals(tag.getId())) {
                throw badRequest("INVALID_TAG_PARENT", "A tag cannot be its own parent");
            }
            if (tagRepository.existsByParent_Id(tag.getId())) {
                throw badRequest("INVALID_TAG_PARENT", "A tag that has children cannot get a parent");
            }
        }
        tag.setName(newName);
        tag.setDescription(normalize(request.description()));
        tag.setParent(parent);
        if (renamed) {
            // Tag names are part of the embedded text of whoever carries the tag.
            entityTagRepository.findAllByTag_Id(tag.getId())
                    .forEach(link -> queueService.enqueueEmbedding(link.getEntityType(), link.getEntityId()));
        }
        return toResponse(tag);
    }

    /** Tags are switched off, never deleted: what was attached to them comes back when switched on. */
    @Transactional
    public RecommendationTagResponse changeStatus(UUID tagId, RecommendationTagStatus status) {
        RecommendationTag tag = find(tagId);
        if (tag.getTagGroup() == RecommendationTagGroup.SEGMENT) {
            throw badRequest("SEGMENT_TAGS_ARE_FIXED",
                    "Level tags are part of the ranking formula and cannot be switched off");
        }
        if (tag.getStatus() != status) {
            tag.setStatus(status);
            tagRepository.flush();
            if (tag.getSubject() != null) {
                enrichPostingsOfSubjectAgain(tag.getSubject().getId());
            }
        }
        return toResponse(tag);
    }

    /**
     * Asks the LLM for topics of one catalogue subject. Read-only: the administrator reviews the
     * list and creates the tags they want through {@link #create}.
     */
    @Transactional(readOnly = true)
    public RecommendationTagSuggestionResponse suggestTopics(UUID subjectId, Integer maxTags) {
        Subject subject = subject(RecommendationTagGroup.TOPIC, subjectId);
        List<VocabularyTag> existing = tagRepository.search(RecommendationTagGroup.TOPIC, subjectId, null).stream()
                .map(tag -> new VocabularyTag(tag.getCode(), tag.getName()))
                .toList();
        SuggestTagsRequest request = new SuggestTagsRequest(
                subject.getName(), existing, samples(subjectId), maxTags == null ? DEFAULT_SUGGESTIONS : maxTags);
        TagSuggestions answer = aiServiceClient.suggestTags(request, suggestionTimeout)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TAG_SUGGESTION_UNAVAILABLE",
                        "Tag suggestions are not available right now; tags can still be entered manually"));

        // Never trust the model: codes must be well-formed and new, parents must be usable.
        Set<String> existingCodes = new HashSet<>();
        existing.forEach(tag -> existingCodes.add(tag.code()));
        Set<String> accepted = new LinkedHashSet<>();
        List<AiServiceClient.SuggestedTag> kept = new ArrayList<>();
        for (AiServiceClient.SuggestedTag tag : answer.tags()) {
            String code = tag.code() == null ? "" : tag.code().strip();
            if (code.matches("^[a-z0-9_]{3,40}$") && tag.name() != null && !tag.name().isBlank()
                    && !accepted.contains(code) && !tagRepository.existsByCode(code)) {
                accepted.add(code);
                kept.add(tag);
            }
        }
        List<Suggestion> suggestions = kept.stream()
                .map(tag -> new Suggestion(
                        tag.code().strip(), limit(tag.name().strip(), 120), limit(normalize(tag.description()), 500),
                        tag.parentCode() != null && !tag.parentCode().equals(tag.code())
                                && (accepted.contains(tag.parentCode()) || existingCodes.contains(tag.parentCode()))
                                ? tag.parentCode() : null))
                .toList();
        return new RecommendationTagSuggestionResponse(subject.getId(), subject.getName(), answer.model(), suggestions);
    }

    /** What users wrote in live postings of the subject, without contact details; a hint for the LLM. */
    private List<String> samples(UUID subjectId) {
        List<String> samples = new ArrayList<>();
        entityManager.createQuery("""
                        select request.title, request.description from TeachingRequest request
                        where request.subject.id = :subjectId
                          and request.status in (com.ptutor.backend.entity.enums.RequestStatus.OPEN,
                                                 com.ptutor.backend.entity.enums.RequestStatus.MATCHED)
                        order by request.createdAt desc
                        """, Object[].class)
                .setParameter("subjectId", subjectId).setMaxResults(SAMPLES_PER_SIDE).getResultList()
                .forEach(row -> addSample(samples, row));
        entityManager.createQuery("""
                        select request.title, request.description, request.learningGoals from StudyingRequest request
                        where request.subject.id = :subjectId
                          and request.status in (com.ptutor.backend.entity.enums.RequestStatus.OPEN,
                                                 com.ptutor.backend.entity.enums.RequestStatus.MATCHED)
                        order by request.createdAt desc
                        """, Object[].class)
                .setParameter("subjectId", subjectId).setMaxResults(SAMPLES_PER_SIDE).getResultList()
                .forEach(row -> addSample(samples, row));
        return samples;
    }

    private void addSample(List<String> samples, Object[] parts) {
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            String scrubbed = textBuilder.scrub((String) part);
            if (!scrubbed.isEmpty()) {
                text.append(text.isEmpty() ? "" : ". ").append(scrubbed);
            }
        }
        if (!text.isEmpty()) {
            samples.add(limit(text.toString(), SAMPLE_MAX_CHARS));
        }
    }

    /**
     * The topics a posting may be tagged with changed: live postings of the subject are enriched
     * again. The topic list is part of their content hash, so they are not skipped as unchanged.
     */
    private void enrichPostingsOfSubjectAgain(UUID subjectId) {
        jobRepository.enqueueEnrichmentForSubject(subjectId, LocalDateTime.now());
    }

    private Subject subject(RecommendationTagGroup group, UUID subjectId) {
        if (group != RecommendationTagGroup.TOPIC) {
            if (subjectId != null) {
                throw badRequest("SUBJECT_NOT_ALLOWED", "Only TOPIC tags belong to a subject");
            }
            return null;
        }
        if (subjectId == null) {
            throw badRequest("SUBJECT_REQUIRED", "A TOPIC tag must belong to a subject");
        }
        return subjectRepository.findById(subjectId)
                .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> badRequest("INVALID_SUBJECT", "Subject not found or inactive"));
    }

    /** A parent is a top-level tag of the same group and subject: only one level of nesting is used. */
    private RecommendationTag parent(
            String code, RecommendationTagGroup group, Subject subject, Map<String, RecommendationTag> createdNow) {
        RecommendationTag parent = createdNow.containsKey(code)
                ? createdNow.get(code) : tagRepository.findFirstByCode(code).orElse(null);
        UUID subjectId = subject == null ? null : subject.getId();
        UUID parentSubjectId = parent == null || parent.getSubject() == null ? null : parent.getSubject().getId();
        if (parent == null || parent.getTagGroup() != group || !Objects.equals(subjectId, parentSubjectId)
                || parent.getParent() != null) {
            throw badRequest("INVALID_TAG_PARENT",
                    "Parent must be an existing top-level tag of the same group and subject: " + code);
        }
        return parent;
    }

    private RecommendationTag find(UUID tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "Tag not found: " + tagId));
    }

    private RecommendationTagResponse toResponse(RecommendationTag tag) {
        return new RecommendationTagResponse(
                tag.getId(), tag.getTagGroup(),
                tag.getSubject() == null ? null : tag.getSubject().getId(),
                tag.getSubject() == null ? null : tag.getSubject().getName(),
                tag.getCode(), tag.getName(), tag.getDescription(),
                tag.getParent() == null ? null : tag.getParent().getId(),
                tag.getParent() == null ? null : tag.getParent().getCode(),
                tag.getStatus());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
