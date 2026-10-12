package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.response.StudentRecommendationResponse;
import com.ptutor.backend.dto.response.StudentRecommendationResponse.Item;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CandidateRepository.Hit;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.StudyingCandidateRepository;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * "Tutor looks for students" (design 06, 6.12): merge the tutor's criteria, hard-filter OPEN
 * studying postings, score each one from both sides and return them best first. The tutor does
 * not need a teaching posting but must have a verified profile, and only limited information about
 * each student is returned.
 */
@Service
@RequiredArgsConstructor
public class StudentRecommendationService {

    private final TutorRepository tutorRepository;
    private final StudyingCandidateRepository candidateRepository;
    private final RecommendationLogRepository logRepository;
    private final SupplyContextBuilder supplyContextBuilder;
    private final QueryEmbeddingService queryEmbeddingService;
    private final TutorSideRanker ranker;
    private final RerankService rerankService;
    private final RecommendationService recommendationService;
    private final RecommendationLogger recommendationLogger;
    private final RecommendationSettings settings;

    /**
     * A new search when {@code logId} is null; otherwise another page of the search that produced
     * that log, served from the stored ranking so the order is stable between pages.
     */
    @Transactional
    public StudentRecommendationResponse recommendStudents(
            UUID userId, StudentRecommendationRequest request, int page, int size, UUID logId) {
        Tutor tutor = tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "TUTOR_PROFILE_NOT_FOUND", "Tutor profile not found"));
        // Students only ever see verified tutors, so an unverified tutor must not reach students either.
        if (tutor.getProfileStatus() != TutorProfileStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_NOT_VERIFIED",
                    "Only tutors with a verified profile can look for students");
        }
        int safePage = Math.max(0, page);
        int safeSize = size < 1
                ? RecommendationService.DEFAULT_PAGE_SIZE : Math.min(size, RecommendationService.MAX_PAGE_SIZE);
        if (logId != null) {
            return pageFromLog(tutor, logId, safePage, safeSize);
        }

        long started = System.nanoTime();
        StudentRecommendationRequest effective = request == null
                ? new StudentRecommendationRequest(null, null) : request;
        SupplyContext context = supplyContextBuilder.forTutor(tutor, effective);
        if (!context.isComplete()) {
            return new StudentRecommendationResponse(
                    null, settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                    context.needsInput(), false, List.of(), List.of(), safePage, safeSize, 0);
        }

        float[] queryVector = queryEmbeddingService.embed(context.supplyText())
                .orElse(tutor.getProfileEmbedding());
        List<Hit> hits = recommendationService.selectHits(
                candidateRepository.findHits(context, queryVector, settings.getPriceHardFactor()));
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        List<ScoredStudyingCandidate> ranked = ranker.rank(context,
                candidateRepository.load(tutor.getId(), hits, settings.getWillingnessHistoryDays()),
                platformRate);
        RerankService.Result rerank = rerankService.forSearch(
                context.supplyText(), RecommendationCandidateType.STUDYING_REQUEST,
                ranked.stream().map(scored -> scored.candidate().studyingRequestId()).toList());
        List<ScoredStudyingCandidate> kept = ranker.applyRerank(context, ranked, rerank.scores(), platformRate)
                .stream().limit(settings.getLogCandidateLimit()).toList();

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        RecommendationLog log = recommendationLogger.logTutorSearch(
                tutor, context, kept, settings.getAlgorithmVersion(), latencyMs, rerank.status());

        List<Item> items = new ArrayList<>();
        int from = Math.min(safePage * safeSize, kept.size());
        int to = Math.min(from + safeSize, kept.size());
        for (int index = from; index < to; index++) {
            items.add(item(index + 1, kept.get(index).candidate(), kept.get(index).score()));
        }
        boolean lowConfidence = kept.stream().limit(RecommendationService.CONFIDENCE_SAMPLE)
                .mapToDouble(scored -> scored.features().confidence()).average().stream()
                .anyMatch(average -> average < settings.getLowConfidenceThreshold());
        return new StudentRecommendationResponse(
                log.getId(), settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                List.of(), lowConfidence, items, List.of(), safePage, safeSize, kept.size());
    }

    private StudentRecommendationResponse pageFromLog(Tutor tutor, UUID logId, int page, int size) {
        RecommendationLog log = logRepository.findById(logId)
                .filter(found -> found.getSearcherTutor() != null
                        && found.getSearcherTutor().getId().equals(tutor.getId())
                        && found.getDirection() == RecommendationDirection.TUTOR_TO_STUDENT)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RECOMMENDATION_LOG_NOT_FOUND", "Recommendation log not found: " + logId));
        List<JsonNode> stored = new ArrayList<>();
        if (log.getReturnedCandidateIds() != null) {
            log.getReturnedCandidateIds().forEach(stored::add);
        }
        int from = Math.min(page * size, stored.size());
        int to = Math.min(from + size, stored.size());
        List<JsonNode> slice = stored.subList(from, to);

        List<Hit> hits = slice.stream()
                .map(node -> new Hit(UUID.fromString(node.path("candidateId").asString()), null, null))
                .toList();
        Map<UUID, StudyingCandidate> loaded = new HashMap<>();
        candidateRepository.load(tutor.getId(), hits, settings.getWillingnessHistoryDays())
                .forEach(candidate -> loaded.put(candidate.studyingRequestId(), candidate));
        List<Item> items = new ArrayList<>();
        for (JsonNode node : slice) {
            StudyingCandidate candidate = loaded.get(UUID.fromString(node.path("candidateId").asString()));
            // A posting deleted since the search simply drops out of the page.
            if (candidate != null) {
                items.add(item(node.path("rank").asInt(), candidate, node.path("score").asDouble()));
            }
        }
        double averageConfidence = stored.stream().limit(RecommendationService.CONFIDENCE_SAMPLE)
                .mapToDouble(node -> node.path("confidence").asDouble(1.0)).average().orElse(1.0);
        JsonNode context = log.getQueryContext();
        if (context != null && context.isObject()) {
            ObjectNode copy = ((ObjectNode) context).deepCopy();
            copy.remove("supplyText");
            copy.remove("rerankStatus");
            context = copy;
        }
        return new StudentRecommendationResponse(
                log.getId(), log.getAlgorithmVersion(), context, List.of(),
                averageConfidence < settings.getLowConfidenceThreshold(), items, List.of(), page, size,
                stored.size());
    }

    /** Only what a tutor may see before any contact: no name, photo, phone number or exact address. */
    private Item item(int rank, StudyingCandidate candidate, double score) {
        String displayName = candidate.gradeName() == null || candidate.gradeName().isBlank()
                ? "Học sinh" : "Học sinh " + candidate.gradeName().strip();
        return new Item(
                rank,
                candidate.studyingRequestId(),
                new StudentRecommendationResponse.Student(
                        displayName, candidate.district() == null ? null : candidate.district().name()),
                candidate.title(),
                candidate.learningGoals(),
                new StudentRecommendationResponse.PriceRange(candidate.minPrice(), candidate.maxPrice()),
                candidate.learningMode(),
                Math.round(score * 1000) / 1000.0,
                false,
                List.of());
    }
}
