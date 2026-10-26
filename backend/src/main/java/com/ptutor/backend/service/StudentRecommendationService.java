package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.History;
import com.ptutor.backend.dto.recommendation.MatchPosition;
import com.ptutor.backend.dto.recommendation.MatchReasons;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.dto.response.StudentRecommendationResponse;
import com.ptutor.backend.dto.response.StudentRecommendationResponse.Item;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CandidateRepository.Hit;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(StudentRecommendationService.class);

    private final TutorRepository tutorRepository;
    private final StudyingCandidateRepository candidateRepository;
    private final RecommendationLogRepository logRepository;
    private final SupplyContextBuilder supplyContextBuilder;
    private final QueryEmbeddingService queryEmbeddingService;
    private final TutorSideRanker ranker;
    private final RerankService rerankService;
    private final MatchReasonBuilder matchReasonBuilder;
    private final RelaxationService relaxationService;
    private final ExplorationService explorationService;
    private final PreferenceProfileService preferenceProfileService;
    private final SavedSearchService savedSearchService;
    private final RecommendationTagRepository tagRepository;
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
        // While it runs, background reranking waits (design 13, 13.2).
        try (RerankQueue.Running running = rerankService.searchRunning()) {
            return searchStudents(userId, request, page, size, logId);
        }
    }

    private StudentRecommendationResponse searchStudents(
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
        PhaseTimer timer = new PhaseTimer();
        StudentRecommendationRequest received = request == null
                ? new StudentRecommendationRequest(null, null) : request;
        // Opening a saved search: its criteria are the search, whatever else the body contains.
        StudentRecommendationRequest effective = received.savedSearchId() == null
                ? received : savedSearchService.tutorRequest(userId, received.savedSearchId());
        SupplyContext context = supplyContextBuilder.forTutor(tutor, effective);
        if (!context.isComplete()) {
            return new StudentRecommendationResponse(
                    null, settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                    context.needsInput(), false, List.of(), List.of(), safePage, safeSize, 0);
        }

        timer.mark("context");
        QueryEmbeddingService.SearchVector searchVector =
                queryEmbeddingService.forSearch(context.supplyText(), tutor.getProfileEmbedding());
        float[] queryVector = searchVector.vector();
        timer.mark("vector");
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        MatchReasonBuilder.TagNames tagNames = MatchReasonBuilder.TagNames.of(
                tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus.ACTIVE));
        History[] history = new History[1];
        List<ScoredStudyingCandidate> ranked = rank(tutor.getId(), context, queryVector, platformRate, history);
        timer.mark("rank");
        RerankService.Result rerank = rerankService.forSearch(
                context.supplyText(), RecommendationCandidateType.STUDYING_REQUEST,
                ranked.stream().map(scored -> scored.candidate().studyingRequestId()).toList());
        timer.mark("rerank");
        List<ScoredStudyingCandidate> all = ranker.applyRerank(context, ranked, rerank.scores(), platformRate);
        ExplorationService.Result<ScoredStudyingCandidate> explored = explorationService.apply(
                all, RecommendationCandidateType.STUDYING_REQUEST,
                scored -> scored.candidate().studyingRequestId(), ScoredStudyingCandidate::score);
        List<ScoredStudyingCandidate> kept =
                explored.ranked().stream().limit(settings.getLogCandidateLimit()).toList();

        Map<String, Double> affinity = history[0].weight() > 0
                ? preferenceProfileService.affinityOf(context.tutorUserId()) : Map.of();
        Map<UUID, MatchReasons> reasons = new HashMap<>();
        kept.forEach(scored -> reasons.put(scored.candidate().studyingRequestId(),
                matchReasonBuilder.forTutor(context, scored, tagNames,
                        explored.explored().contains(scored.candidate().studyingRequestId()),
                        RecommendationService.likedTags(tagCodes(scored.candidate()), affinity, tagNames))));
        timer.mark("explore+reasons");
        List<RelaxationSuggestion> relaxations = relaxationService.suggest(
                all, relaxationService.forTutor(context),
                relaxed -> rank(tutor.getId(), relaxed, queryVector, platformRate, new History[1]),
                scored -> scored.candidate().studentId(), ScoredStudyingCandidate::score,
                relaxationService::tutorMessage);
        timer.mark("relax");

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        RecommendationLog log = recommendationLogger.logTutorSearch(
                tutor, context, kept, settings.getAlgorithmVersion(), latencyMs, rerank.status(),
                searchVector.source(), reasons, relaxations, explored.explored(), effective.savedSearchId());
        timer.mark("log");
        LOGGER.debug("Tutor search {} phases (ms): {}", log.getId(), timer);

        List<Item> items = new ArrayList<>();
        int from = Math.min(safePage * safeSize, kept.size());
        int to = Math.min(from + safeSize, kept.size());
        for (int index = from; index < to; index++) {
            ScoredStudyingCandidate scored = kept.get(index);
            items.add(item(index + 1, scored.candidate(), scored.score(),
                    reasons.get(scored.candidate().studyingRequestId()),
                    explored.explored().contains(scored.candidate().studyingRequestId())));
        }
        boolean lowConfidence = kept.stream().limit(RecommendationService.CONFIDENCE_SAMPLE)
                .mapToDouble(scored -> scored.features().confidence()).average().stream()
                .anyMatch(average -> average < settings.getLowConfidenceThreshold());
        return new StudentRecommendationResponse(
                log.getId(), settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                List.of(), lowConfidence, items, relaxations, safePage, safeSize, kept.size());
    }

    /**
     * Where a given studying posting would land if this tutor ran this search now (design 07, 7.3).
     * Phases 1-4 without the exploration slots; nothing is logged. Only verified tutors are matched.
     */
    @Transactional
    public Optional<MatchPosition> matchPosition(
            UUID tutorUserId, StudentRecommendationRequest request, UUID studyingRequestId) {
        Tutor tutor = tutorRepository.findByUser_Id(tutorUserId).orElse(null);
        if (tutor == null || tutor.getProfileStatus() != TutorProfileStatus.VERIFIED) {
            return Optional.empty();
        }
        SupplyContext context = supplyContextBuilder.forTutor(tutor, request);
        if (!context.isComplete()) {
            return Optional.empty();
        }
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        float[] queryVector = queryEmbeddingService.embed(context.supplyText()).orElse(tutor.getProfileEmbedding());
        List<ScoredStudyingCandidate> ranked = rank(tutor.getId(), context, queryVector, platformRate, new History[1]);
        if (ranked.stream().noneMatch(scored -> scored.candidate().studyingRequestId().equals(studyingRequestId))) {
            return Optional.empty();
        }
        RerankService.Result rerank = rerankService.prefetch(
                context.supplyText(), RecommendationCandidateType.STUDYING_REQUEST,
                ranked.stream().map(scored -> scored.candidate().studyingRequestId()).toList());
        List<ScoredStudyingCandidate> all = ranker.applyRerank(context, ranked, rerank.scores(), platformRate);
        MatchReasonBuilder.TagNames tagNames = MatchReasonBuilder.TagNames.of(
                tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus.ACTIVE));
        for (int index = 0; index < all.size(); index++) {
            ScoredStudyingCandidate scored = all.get(index);
            if (scored.candidate().studyingRequestId().equals(studyingRequestId)) {
                List<String> reasons = matchReasonBuilder.forTutor(context, scored, tagNames).reasons();
                // The student is not named: a tutor only learns who they are after contact.
                return Optional.of(new MatchPosition(index + 1, scored.score(),
                        reasons.isEmpty() ? null : reasons.get(0), scored.candidate().title(), null));
            }
        }
        return Optional.empty();
    }

    /** Scores ahead of time the pairs a saved search of this tutor would need. */
    @Transactional
    public int prefetchRerankForSavedSearch(UUID tutorUserId, UUID savedSearchId) {
        Tutor tutor = tutorRepository.findByUser_Id(tutorUserId).orElse(null);
        if (tutor == null || tutor.getProfileStatus() != TutorProfileStatus.VERIFIED) {
            return 0;
        }
        SupplyContext context = supplyContextBuilder.forTutor(
                tutor, savedSearchService.tutorRequest(tutorUserId, savedSearchId));
        if (!context.isComplete()) {
            return 0;
        }
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        float[] queryVector = queryEmbeddingService.embed(context.supplyText()).orElse(tutor.getProfileEmbedding());
        return rerankService.prefetch(context.supplyText(), RecommendationCandidateType.STUDYING_REQUEST,
                rank(tutor.getId(), context, queryVector, platformRate, new History[1]).stream()
                        .map(scored -> scored.candidate().studyingRequestId()).toList()).computed();
    }

    /** Phases 1-3, then one result per student: only the best of a student's postings is shown. */
    private List<ScoredStudyingCandidate> rank(
            UUID tutorId, SupplyContext context, float[] queryVector, double platformRate, History[] historyOut) {
        List<Hit> hits = recommendationService.selectHits(
                candidateRepository.findHits(context, queryVector, settings.getPriceHardFactor()));
        List<StudyingCandidate> candidates =
                candidateRepository.load(tutorId, hits, settings.getWillingnessHistoryDays());
        Map<UUID, Set<String>> tagCodes = new HashMap<>();
        candidates.forEach(candidate -> tagCodes.put(candidate.studyingRequestId(), tagCodes(candidate)));
        History history = preferenceProfileService.historyFor(
                context.tutorUserId(), RecommendationCandidateType.STUDYING_REQUEST, tagCodes);
        historyOut[0] = history;
        return RecommendationService.bestPerPerson(ranker.rank(context, candidates, platformRate, history),
                scored -> scored.candidate().studentId());
    }

    /** Codes of everything the posting and its student are tagged with. */
    private Set<String> tagCodes(StudyingCandidate candidate) {
        Set<String> codes = new java.util.HashSet<>(candidate.preferredStyleCodes());
        candidate.needTags().forEach(tag -> codes.add(tag.code()));
        if (candidate.levelCode() != null) {
            codes.add(candidate.levelCode());
        }
        return codes;
    }

    private StudentRecommendationResponse pageFromLog(Tutor tutor, UUID logId, int page, int size) {
        RecommendationLog log = logRepository.findById(logId)
                .filter(found -> found.getSearcherTutor() != null
                        && found.getSearcherTutor().getId().equals(tutor.getId())
                        && found.getDirection() == RecommendationDirection.TUTOR_TO_STUDENT)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RECOMMENDATION_LOG_NOT_FOUND", "Recommendation log not found: " + logId));
        return pageOf(log, page * size, size, page);
    }

    /** The results of a stored search after the given rank, for the in-session refinement. */
    @Transactional(readOnly = true)
    public StudentRecommendationResponse resultsAfter(RecommendationLog log, int afterRank) {
        return pageOf(log, Math.max(0, afterRank), settings.getLogCandidateLimit(), 0);
    }

    private StudentRecommendationResponse pageOf(RecommendationLog log, int offset, int size, int page) {
        List<JsonNode> stored = new ArrayList<>();
        if (log.getReturnedCandidateIds() != null) {
            log.getReturnedCandidateIds().forEach(stored::add);
        }
        int from = Math.min(offset, stored.size());
        int to = Math.min(from + size, stored.size());
        List<JsonNode> slice = stored.subList(from, to);

        List<Hit> hits = slice.stream()
                .map(node -> new Hit(UUID.fromString(node.path("candidateId").asString()), null, null))
                .toList();
        Map<UUID, StudyingCandidate> loaded = new HashMap<>();
        candidateRepository.load(log.getSearcherTutor().getId(), hits, settings.getWillingnessHistoryDays())
                .forEach(candidate -> loaded.put(candidate.studyingRequestId(), candidate));
        List<Item> items = new ArrayList<>();
        for (JsonNode node : slice) {
            StudyingCandidate candidate = loaded.get(UUID.fromString(node.path("candidateId").asString()));
            // A posting deleted since the search simply drops out of the page.
            if (candidate != null) {
                items.add(item(node.path("rank").asInt(), candidate, node.path("score").asDouble(),
                        recommendationLogger.reasons(node), node.path("isExploration").asBoolean(false)));
            }
        }
        double averageConfidence = stored.stream().limit(RecommendationService.CONFIDENCE_SAMPLE)
                .mapToDouble(node -> node.path("confidence").asDouble(1.0)).average().orElse(1.0);
        JsonNode context = log.getQueryContext();
        if (context != null && context.isObject()) {
            ObjectNode copy = ((ObjectNode) context).deepCopy();
            copy.remove("supplyText");
            copy.remove("rerankStatus");
            copy.remove("queryVector");
            context = copy;
        }
        return new StudentRecommendationResponse(
                log.getId(), log.getAlgorithmVersion(), context, List.of(),
                averageConfidence < settings.getLowConfidenceThreshold(), items,
                recommendationLogger.relaxations(log.getRelaxationSuggestions()), page, size, stored.size());
    }

    /** Only what a tutor may see before any contact: no name, photo, phone number or exact address. */
    private Item item(
            int rank, StudyingCandidate candidate, double score, MatchReasons reasons, boolean exploration) {
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
                exploration,
                reasons == null ? List.of() : reasons.reasons(),
                reasons == null ? null : reasons.caution());
    }
}
