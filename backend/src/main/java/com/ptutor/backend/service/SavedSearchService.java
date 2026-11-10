package com.ptutor.backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.request.SavedSearchRequest;
import com.ptutor.backend.dto.request.SavedSearchUpdateRequest;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.dto.response.SavedSearchResponse;
import com.ptutor.backend.entity.RecommendationSavedSearch;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.RecommendationSavedSearchRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Saved searches (design 08, 8.6): a set of criteria a user wants to come back to, and - when
 * notifications are on - to be told about when a new posting fits it very well (C7). Thanks to
 * them a user without any posting of their own can still be notified.
 */
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private final RecommendationSavedSearchRepository savedSearchRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final QueryContextBuilder queryContextBuilder;
    private final SupplyContextBuilder supplyContextBuilder;
    private final EmbeddingTextBuilder textBuilder;
    private final RecommendationSettings settings;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> findMine(UUID userId) {
        return savedSearchRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /**
     * The criteria are checked the same way a search checks them, so a saved search can always be
     * run: unknown subjects are rejected and the required criteria (subject and grade) must be there.
     */
    @Transactional
    public SavedSearchResponse create(UUID userId, SavedSearchRequest request) {
        if (savedSearchRepository.countByUser_Id(userId) >= settings.getSavedSearchLimit()) {
            throw new ApiException(HttpStatus.CONFLICT, "SAVED_SEARCH_LIMIT_REACHED",
                    "At most " + settings.getSavedSearchLimit() + " searches can be saved");
        }
        String semanticText = textBuilder.scrub(request.semanticText());
        ObjectNode stored = objectMapper.createObjectNode();
        String searchText;
        if (request.direction() == RecommendationDirection.STUDENT_TO_TUTOR) {
            Student student = studentRepository.findProfileByUserId(userId).orElseThrow(this::wrongDirection);
            TutorRecommendationRequest.Filters filters = read(request.filters(), TutorRecommendationRequest.Filters.class);
            QueryContext context = queryContextBuilder.forStudent(student,
                    new TutorRecommendationRequest(filters, semanticText.isEmpty() ? null : semanticText, null, null));
            requireComplete(context.needsInput());
            stored.set("filters", objectMapper.valueToTree(filters));
            searchText = context.demandText();
        } else {
            Tutor tutor = tutorRepository.findByUser_Id(userId).orElseThrow(this::wrongDirection);
            StudentRecommendationRequest.Filters filters =
                    read(request.filters(), StudentRecommendationRequest.Filters.class);
            SupplyContext context = supplyContextBuilder.forTutor(tutor, new StudentRecommendationRequest(filters, null));
            requireComplete(context.needsInput());
            stored.set("filters", objectMapper.valueToTree(filters));
            searchText = context.supplyText();
        }
        stored.put("semanticText", semanticText.isEmpty() ? null : semanticText);
        RecommendationSavedSearch saved = savedSearchRepository.save(RecommendationSavedSearch.builder()
                .user(entityManager.getReference(User.class, userId))
                .direction(request.direction())
                .name(normalize(request.name()))
                .queryContext(stored)
                .queryTextHash(textBuilder.hash(searchText))
                .notifyEnabled(request.notifyEnabled() == null || request.notifyEnabled())
                .build());
        return toResponse(saved);
    }

    /** Renames the search and / or switches its notifications; the criteria themselves do not change. */
    @Transactional
    public SavedSearchResponse update(UUID userId, UUID savedSearchId, SavedSearchUpdateRequest request) {
        RecommendationSavedSearch saved = owned(userId, savedSearchId);
        if (request.name() != null) {
            saved.setName(normalize(request.name()));
        }
        if (request.notifyEnabled() != null) {
            saved.setNotifyEnabled(request.notifyEnabled());
        }
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID savedSearchId) {
        savedSearchRepository.delete(owned(userId, savedSearchId));
    }

    /** The saved criteria of a student, as the request a search would have received. */
    @Transactional(readOnly = true)
    public TutorRecommendationRequest studentRequest(UUID userId, UUID savedSearchId) {
        RecommendationSavedSearch saved = owned(userId, savedSearchId, RecommendationDirection.STUDENT_TO_TUTOR);
        JsonNode stored = saved.getQueryContext();
        return new TutorRecommendationRequest(
                read(stored.path("filters"), TutorRecommendationRequest.Filters.class),
                stored.path("semanticText").isString() ? stored.path("semanticText").asString() : null,
                null, null, saved.getId());
    }

    /** The saved criteria of a tutor, as the request a search would have received. */
    @Transactional(readOnly = true)
    public StudentRecommendationRequest tutorRequest(UUID userId, UUID savedSearchId) {
        RecommendationSavedSearch saved = owned(userId, savedSearchId, RecommendationDirection.TUTOR_TO_STUDENT);
        return new StudentRecommendationRequest(
                read(saved.getQueryContext().path("filters"), StudentRecommendationRequest.Filters.class),
                null, saved.getId());
    }

    private RecommendationSavedSearch owned(UUID userId, UUID savedSearchId, RecommendationDirection direction) {
        RecommendationSavedSearch saved = owned(userId, savedSearchId);
        if (saved.getDirection() != direction) {
            throw notFound(savedSearchId);
        }
        return saved;
    }

    private RecommendationSavedSearch owned(UUID userId, UUID savedSearchId) {
        return savedSearchRepository.findByIdAndUser_Id(savedSearchId, userId).orElseThrow(() -> notFound(savedSearchId));
    }

    private <T> T read(JsonNode filters, Class<T> type) {
        T value;
        try {
            value = filters == null || filters.isNull() || filters.isMissingNode()
                    ? objectMapper.treeToValue(objectMapper.createObjectNode(), type)
                    : objectMapper.treeToValue(filters, type);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SAVED_SEARCH", "The filters cannot be read");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SAVED_SEARCH",
                    violations.iterator().next().getMessage());
        }
        return value;
    }

    private void requireComplete(List<String> needsInput) {
        if (!needsInput.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAVED_SEARCH_INCOMPLETE",
                    "A saved search needs: " + String.join(", ", needsInput));
        }
    }

    private SavedSearchResponse toResponse(RecommendationSavedSearch saved) {
        JsonNode stored = saved.getQueryContext();
        return new SavedSearchResponse(saved.getId(), saved.getDirection(), saved.getName(), stored.path("filters"),
                stored.path("semanticText").isString() ? stored.path("semanticText").asString() : null,
                Boolean.TRUE.equals(saved.getNotifyEnabled()), saved.getLastNotifiedAt(), saved.getCreatedAt());
    }

    private String normalize(String name) {
        return name == null || name.isBlank() ? null : name.strip();
    }

    private ApiException wrongDirection() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SAVED_SEARCH_DIRECTION_NOT_ALLOWED",
                "Students save searches for tutors and tutors save searches for students");
    }

    private ApiException notFound(UUID savedSearchId) {
        return new ApiException(HttpStatus.NOT_FOUND, "SAVED_SEARCH_NOT_FOUND", "Saved search not found: " + savedSearchId);
    }
}
