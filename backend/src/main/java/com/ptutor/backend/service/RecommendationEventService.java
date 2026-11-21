package com.ptutor.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.entity.RecommendationClickLog;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.FavoriteTutorRepository;
import com.ptutor.backend.repository.RecommendationClickLogRepository;
import com.ptutor.backend.repository.RecommendationLogRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Records what a user does with the results of one of their searches (design 08, 8.5): opened,
 * liked, contacted, dismissed. These rows feed the in-session refinement (B5), the long-term
 * preference profile (H1) and the exposure statistics (R6).
 */
@Service
@RequiredArgsConstructor
public class RecommendationEventService {

    private final RecommendationLogRepository logRepository;
    private final RecommendationClickLogRepository clickLogRepository;
    private final FavoriteTutorRepository favoriteTutorRepository;
    private final AiEnrichmentJobRepository jobRepository;
    private final EntityManager entityManager;

    @Transactional
    public void record(UUID userId, UUID logId, RecommendationEventRequest request) {
        RecommendationLog log = ownedLog(userId, logId);
        JsonNode stored = candidate(log, request.candidateId());
        if (stored == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANDIDATE_NOT_IN_RESULTS",
                    "This posting is not among the results of the search");
        }
        // The rank is the one the search gave, not whatever the client sends.
        RecommendationClickLog.RecommendationClickLogBuilder click = RecommendationClickLog.builder()
                .recommendationLog(log).action(request.action()).rank(stored.path("rank").asInt());
        LocalDateTime now = LocalDateTime.now();
        if (log.getDirection() == RecommendationDirection.STUDENT_TO_TUTOR) {
            TeachingRequest posting = entityManager.find(TeachingRequest.class, request.candidateId());
            if (posting == null) {
                throw gone();
            }
            clickLogRepository.save(click.candidateTeachingRequest(posting).clickedTutor(posting.getTutor()).build());
            if (request.action() == RecommendationAction.FAVORITED) {
                favoriteTutorRepository.addOrRestore(
                        UUID.randomUUID(), log.getSearcherStudent().getId(), posting.getTutor().getId(),
                        posting.getId(), now);
            } else if (request.action() == RecommendationAction.DISMISSED) {
                favoriteTutorRepository.remove(log.getSearcherStudent().getId(), posting.getTutor().getId(), now);
            }
            jobRepository.enqueue(AiJobType.PROFILE_UPDATE.name(), RecommendationEntityType.STUDENT.name(),
                    log.getSearcherStudent().getId(), now);
        } else {
            StudyingRequest posting = entityManager.find(StudyingRequest.class, request.candidateId());
            if (posting == null) {
                throw gone();
            }
            clickLogRepository.save(click.candidateStudyingRequest(posting).clickedStudent(posting.getStudent()).build());
            jobRepository.enqueue(AiJobType.PROFILE_UPDATE.name(), RecommendationEntityType.TUTOR.name(),
                    log.getSearcherTutor().getId(), now);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> favoriteTutorUserIds(UUID userId) {
        return favoriteTutorRepository.findTutorUserIdsByStudentUserId(userId);
    }

    @Transactional
    public void removeFavoriteTutor(UUID userId, UUID tutorId) {
        var latestClick = clickLogRepository
                .findFirstByRecommendationLog_SearcherStudent_User_IdAndClickedTutor_IdOrderByCreatedAtDesc(
                        userId, tutorId);
        if (latestClick.isPresent() && latestClick.get().getCandidateTeachingRequest() != null) {
            RecommendationClickLog click = latestClick.get();
            record(userId, click.getRecommendationLog().getId(), new RecommendationEventRequest(
                    RecommendationAction.DISMISSED, null, click.getCandidateTeachingRequest().getId()));
            return;
        }
        favoriteTutorRepository.removeForStudentUser(userId, tutorId, LocalDateTime.now());
    }

    /** The log of a search, only for the person who ran it; anyone else gets "not found". */
    @Transactional(readOnly = true)
    public RecommendationLog ownedLog(UUID userId, UUID logId) {
        return logRepository.findById(logId)
                .filter(log -> log.getDirection() != null && userId.equals(searcherUserId(log)))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RECOMMENDATION_LOG_NOT_FOUND", "Recommendation log not found: " + logId));
    }

    private UUID searcherUserId(RecommendationLog log) {
        if (log.getSearcherStudent() != null && log.getSearcherStudent().getUser() != null) {
            return log.getSearcherStudent().getUser().getId();
        }
        if (log.getSearcherTutor() != null && log.getSearcherTutor().getUser() != null) {
            return log.getSearcherTutor().getUser().getId();
        }
        return null;
    }

    private JsonNode candidate(RecommendationLog log, UUID candidateId) {
        if (log.getReturnedCandidateIds() != null) {
            for (JsonNode node : log.getReturnedCandidateIds()) {
                if (candidateId.toString().equals(node.path("candidateId").asString())) {
                    return node;
                }
            }
        }
        return null;
    }

    private ApiException gone() {
        return new ApiException(HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "This posting no longer exists");
    }
}
