package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.History;
import com.ptutor.backend.dto.recommendation.MatchPosition;
import com.ptutor.backend.dto.recommendation.MatchReasons;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.dto.response.TutorRecommendationResponse;
import com.ptutor.backend.dto.response.TutorRecommendationResponse.Item;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CandidateRepository;
import com.ptutor.backend.repository.CandidateRepository.Hit;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.StudentRepository;

import tools.jackson.databind.JsonNode;

/**
 * "Student looks for tutors", version V2-RCX: merge the criteria, hard-filter OPEN teaching
 * postings, score each one from both sides (how well it suits the student, how likely the tutor
 * is to accept), keep the best posting of each tutor, rerank the best ones with a cross-encoder
 * and return them best first, each with the reasons it matches. When few good results come out,
 * ways to loosen the search are suggested.
 */
@Service
public class RecommendationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /** How many of the best results decide whether the whole search is flagged as low confidence. */
    static final int CONFIDENCE_SAMPLE = 10;

    private final StudentRepository studentRepository;
    private final CandidateRepository candidateRepository;
    private final RecommendationLogRepository logRepository;
    private final QueryContextBuilder queryContextBuilder;
    private final QueryEmbeddingService queryEmbeddingService;
    private final Ranker ranker;
    private final RerankService rerankService;
    private final MatchReasonBuilder matchReasonBuilder;
    private final RelaxationService relaxationService;
    private final ExplorationService explorationService;
    private final PreferenceProfileService preferenceProfileService;
    private final SavedSearchService savedSearchService;
    private final QueryParseService queryParseService;
    private final RecommendationTagRepository tagRepository;
    private final RecommendationLogger recommendationLogger;
    private final RecommendationSettings settings;
    private final CertificateFileStorageService fileStorageService;

    @Autowired
    public RecommendationService(
            StudentRepository studentRepository,
            CandidateRepository candidateRepository,
            RecommendationLogRepository logRepository,
            QueryContextBuilder queryContextBuilder,
            QueryEmbeddingService queryEmbeddingService,
            Ranker ranker,
            RerankService rerankService,
            MatchReasonBuilder matchReasonBuilder,
            RelaxationService relaxationService,
            ExplorationService explorationService,
            PreferenceProfileService preferenceProfileService,
            SavedSearchService savedSearchService,
            QueryParseService queryParseService,
            RecommendationTagRepository tagRepository,
            RecommendationLogger recommendationLogger,
            RecommendationSettings settings,
            CertificateFileStorageService fileStorageService) {
        this.studentRepository = studentRepository;
        this.candidateRepository = candidateRepository;
        this.logRepository = logRepository;
        this.queryContextBuilder = queryContextBuilder;
        this.queryEmbeddingService = queryEmbeddingService;
        this.ranker = ranker;
        this.rerankService = rerankService;
        this.matchReasonBuilder = matchReasonBuilder;
        this.relaxationService = relaxationService;
        this.explorationService = explorationService;
        this.preferenceProfileService = preferenceProfileService;
        this.savedSearchService = savedSearchService;
        this.queryParseService = queryParseService;
        this.tagRepository = tagRepository;
        this.recommendationLogger = recommendationLogger;
        this.settings = settings;
        this.fileStorageService = fileStorageService;
    }

    /**
     * A new search when {@code logId} is null; otherwise another page of the search that produced
     * that log, served from the stored ranking so the order is stable between pages.
     */
    @Transactional
    public TutorRecommendationResponse recommendTutors(
            UUID userId, TutorRecommendationRequest request, int page, int size, UUID logId) {
        Student student = studentRepository.findProfileByUserId(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND", "Student profile not found"));
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        if (logId != null) {
            return pageFromLog(student, logId, safePage, safeSize);
        }

        long started = System.nanoTime();
        PhaseTimer timer = new PhaseTimer();
        TutorRecommendationRequest received = request == null
                ? new TutorRecommendationRequest(null, null, null, null) : request;
        // Opening a saved search: its criteria are the search, whatever else the body contains.
        TutorRecommendationRequest effective = received.savedSearchId() == null
                ? received : savedSearchService.studentRequest(userId, received.savedSearchId());
        QueryContext context = queryContextBuilder.forStudent(student, effective);
        if (!context.isComplete()) {
            return new TutorRecommendationResponse(
                    null, settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                    context.needsInput(), false, List.of(), List.of(), safePage, safeSize, 0);
        }

        MatchReasonBuilder.TagNames tagNames = MatchReasonBuilder.TagNames.of(
                tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus.ACTIVE));
        timer.mark("context");
        float[] queryVector = queryVector(student, context);
        timer.mark("vector");
        Ranking ranking = rank(student.getId(), context, queryVector, tagNames);
        timer.mark("rank");
        RerankService.Result rerank = rerankService.forSearch(
                context.demandText(), RecommendationCandidateType.TEACHING_REQUEST, ids(ranking.ranked()));
        timer.mark("rerank");
        List<ScoredCandidate> all = ranker
                .applyRerank(context, ranking.ranked(), rerank.scores(), ranking.platformRate());
        ExplorationService.Result<ScoredCandidate> explored = explorationService.apply(
                all, RecommendationCandidateType.TEACHING_REQUEST,
                scored -> scored.candidate().teachingRequestId(), ScoredCandidate::score);
        List<ScoredCandidate> kept = explored.ranked().stream().limit(settings.getLogCandidateLimit()).toList();

        Map<String, Double> affinity = ranking.history().weight() > 0
                ? preferenceProfileService.affinityOf(context.studentUserId()) : Map.of();
        Map<UUID, MatchReasons> reasons = new HashMap<>();
        kept.forEach(scored -> reasons.put(scored.candidate().teachingRequestId(),
                matchReasonBuilder.forStudent(context, scored, tagNames,
                        explored.explored().contains(scored.candidate().teachingRequestId()),
                        likedTags(tagCodes(scored.candidate(), tagNames), affinity, tagNames))));
        timer.mark("explore+reasons");
        List<RelaxationSuggestion> relaxations = relaxationService.suggest(
                all, relaxationService.forStudent(context),
                relaxed -> rank(student.getId(), relaxed, queryVector, tagNames).ranked(),
                scored -> scored.candidate().tutorId(), ScoredCandidate::score, relaxationService::studentMessage);
        timer.mark("relax");

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        RecommendationLog log = recommendationLogger.logStudentSearch(
                student, context, effective.queryText(), kept, settings.getAlgorithmVersion(), latencyMs,
                rerank.status(), reasons, relaxations, explored.explored(), effective.savedSearchId(),
                // What the sentence was understood as, next to what was finally searched (design 05, 5.7).
                effective.queryText() == null ? null : queryParseService.recall(context.homeProvinceId(),
                        effective.queryText(), RecommendationDirection.STUDENT_TO_TUTOR).orElse(null));
        timer.mark("log");
        LOGGER.debug("Student search {} phases (ms): {}", log.getId(), timer);

        List<Item> items = new ArrayList<>();
        int from = Math.min(safePage * safeSize, kept.size());
        int to = Math.min(from + safeSize, kept.size());
        for (int index = from; index < to; index++) {
            ScoredCandidate scored = kept.get(index);
            items.add(item(index + 1, scored.candidate(), scored.score(),
                    reasons.get(scored.candidate().teachingRequestId()),
                    explored.explored().contains(scored.candidate().teachingRequestId())));
        }
        return new TutorRecommendationResponse(
                log.getId(), settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                List.of(), isLowConfidence(kept), items, relaxations, safePage, safeSize, kept.size());
    }

    /**
     * Scores ahead of time the pairs a search from this studying posting would need (design 07, 7.3).
     * Nothing is logged and nothing is shown to anyone.
     *
     * @return how many pairs the model scored
     */
    @Transactional
    public int prefetchRerank(UUID studentUserId, UUID studyingRequestId) {
        Student student = studentRepository.findProfileByUserId(studentUserId).orElse(null);
        if (student == null) {
            return 0;
        }
        // Exactly what the student sends when searching from the posting without changing anything.
        return prefetchRerank(student, new TutorRecommendationRequest(null, null, null, studyingRequestId));
    }

    /** The same for the criteria of a saved search of this student. */
    @Transactional
    public int prefetchRerankForSavedSearch(UUID studentUserId, UUID savedSearchId) {
        Student student = studentRepository.findProfileByUserId(studentUserId).orElse(null);
        return student == null ? 0
                : prefetchRerank(student, savedSearchService.studentRequest(studentUserId, savedSearchId));
    }

    private int prefetchRerank(Student student, TutorRecommendationRequest request) {
        QueryContext context = queryContextBuilder.forStudent(student, request);
        if (!context.isComplete()) {
            return 0;
        }
        return rerankService.prefetch(context.demandText(), RecommendationCandidateType.TEACHING_REQUEST,
                ids(rank(student.getId(), context, queryVector(student, context), activeTagNames()).ranked()))
                .computed();
    }

    /**
     * Where a given teaching posting would land if this student ran this search now (design 07, 7.3).
     * Phases 1-4 without the exploration slots; nothing is logged. Empty when the posting does not
     * pass the hard filters, or when another posting of the same tutor ranks above it.
     */
    @Transactional
    public Optional<MatchPosition> matchPosition(
            UUID studentUserId, TutorRecommendationRequest request, UUID teachingRequestId) {
        Student student = studentRepository.findProfileByUserId(studentUserId).orElse(null);
        if (student == null) {
            return Optional.empty();
        }
        QueryContext context = queryContextBuilder.forStudent(student, request);
        if (!context.isComplete()) {
            return Optional.empty();
        }
        MatchReasonBuilder.TagNames tagNames = activeTagNames();
        Ranking ranking = rank(student.getId(), context, queryVector(student, context), tagNames);
        if (ranking.ranked().stream().noneMatch(
                scored -> scored.candidate().teachingRequestId().equals(teachingRequestId))) {
            return Optional.empty();
        }
        // In the background there is time to score every pair, unlike in a search.
        RerankService.Result rerank = rerankService.prefetch(
                context.demandText(), RecommendationCandidateType.TEACHING_REQUEST, ids(ranking.ranked()));
        List<ScoredCandidate> ranked = ranker
                .applyRerank(context, ranking.ranked(), rerank.scores(), ranking.platformRate());
        for (int index = 0; index < ranked.size(); index++) {
            ScoredCandidate scored = ranked.get(index);
            if (scored.candidate().teachingRequestId().equals(teachingRequestId)) {
                List<String> reasons = matchReasonBuilder.forStudent(context, scored, tagNames).reasons();
                return Optional.of(new MatchPosition(index + 1, scored.score(),
                        reasons.isEmpty() ? null : reasons.get(0), scored.candidate().title(),
                        scored.candidate().tutorDisplayName()));
            }
        }
        return Optional.empty();
    }

    private MatchReasonBuilder.TagNames activeTagNames() {
        return MatchReasonBuilder.TagNames.of(
                tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus.ACTIVE));
    }

    /** Without a usable query vector the ranking still works, only without the semantic feature. */
    private float[] queryVector(Student student, QueryContext context) {
        return queryEmbeddingService.embed(context.demandText()).orElse(student.getProfileEmbedding());
    }

    /**
     * Phases 1-3 (hard filter, features of both sides, final score), then one result per tutor:
     * a tutor with several matching postings is shown once, with the best one (design 06, 6.10).
     */
    private Ranking rank(
            UUID studentId, QueryContext context, float[] queryVector, MatchReasonBuilder.TagNames tagNames) {
        List<Hit> hits = selectHits(
                candidateRepository.findHits(context, queryVector, settings.getPriceHardFactor()));
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        List<TeachingCandidate> candidates =
                candidateRepository.load(studentId, hits, settings.getWillingnessHistoryDays());
        Map<UUID, Set<String>> tagCodes = new HashMap<>();
        candidates.forEach(candidate -> tagCodes.put(candidate.teachingRequestId(), tagCodes(candidate, tagNames)));
        History history = preferenceProfileService.historyFor(
                context.studentUserId(), RecommendationCandidateType.TEACHING_REQUEST, tagCodes);
        return new Ranking(bestPerPerson(ranker.rank(context, candidates, platformRate, history),
                scored -> scored.candidate().tutorId()), platformRate, history);
    }

    /** Codes of everything the posting and its tutor are tagged with. */
    private Set<String> tagCodes(TeachingCandidate candidate, MatchReasonBuilder.TagNames tagNames) {
        Set<String> codes = new java.util.HashSet<>(candidate.offeredStyleCodes());
        codes.addAll(candidate.targetSegmentCodes());
        candidate.strengthTags().forEach(tag -> {
            String code = tagNames.codeById().get(tag.tagId());
            if (code != null) {
                codes.add(code);
            }
        });
        return codes;
    }

    /** Names of the tags of a posting the searcher showed most interest in before, best first. */
    static List<String> likedTags(
            Set<String> candidateCodes, Map<String, Double> affinity, MatchReasonBuilder.TagNames tagNames) {
        return candidateCodes.stream()
                .filter(code -> affinity.getOrDefault(code, 0.0) > 0 && tagNames.nameByCode().containsKey(code))
                .sorted(java.util.Comparator.comparingDouble((String code) -> affinity.get(code)).reversed()
                        .thenComparing(java.util.Comparator.naturalOrder()))
                .map(tagNames.nameByCode()::get).limit(2).toList();
    }

    /** Keeps the first (best ranked) result of every person and drops their other postings. */
    static <S> List<S> bestPerPerson(List<S> ranked, Function<S, UUID> person) {
        Set<UUID> seen = new java.util.HashSet<>();
        return ranked.stream().filter(scored -> seen.add(person.apply(scored))).toList();
    }

    private List<UUID> ids(List<ScoredCandidate> ranked) {
        return ranked.stream().map(scored -> scored.candidate().teachingRequestId()).toList();
    }

    private record Ranking(List<ScoredCandidate> ranked, double platformRate, History history) {
    }

    /**
     * At most {@code candidate-limit} postings go on to scoring: the nearest ones by vector, plus a
     * few of the newest postings that have no embedding yet so that they are not invisible.
     */
    List<Hit> selectHits(List<Hit> hits) {
        int limit = settings.getCandidateLimit();
        if (hits.size() <= limit) {
            return hits;
        }
        Comparator<Hit> newestFirst = Comparator.comparing(Hit::createdAt).reversed();
        List<Hit> withCosine = hits.stream().filter(hit -> hit.cosine() != null)
                .sorted(Comparator.comparingDouble((Hit hit) -> hit.cosine()).reversed()).toList();
        List<Hit> withoutCosine = hits.stream().filter(hit -> hit.cosine() == null).sorted(newestFirst).toList();
        int unembedded = Math.min(withoutCosine.size(),
                Math.max(settings.getCandidateUnembeddedQuota(), limit - withCosine.size()));
        List<Hit> selected = new ArrayList<>(withCosine.subList(0, Math.min(withCosine.size(), limit - unembedded)));
        selected.addAll(withoutCosine.subList(0, unembedded));
        return selected;
    }

    private boolean isLowConfidence(List<ScoredCandidate> ranked) {
        return ranked.stream().limit(CONFIDENCE_SAMPLE)
                .mapToDouble(scored -> scored.features().confidence())
                .average()
                .stream()
                .anyMatch(average -> average < settings.getLowConfidenceThreshold());
    }

    private TutorRecommendationResponse pageFromLog(Student student, UUID logId, int page, int size) {
        RecommendationLog log = logRepository.findById(logId)
                .filter(found -> found.getSearcherStudent() != null
                        && found.getSearcherStudent().getId().equals(student.getId())
                        && found.getDirection() == RecommendationDirection.STUDENT_TO_TUTOR)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RECOMMENDATION_LOG_NOT_FOUND", "Recommendation log not found: " + logId));
        return pageOf(log, page * size, size, page);
    }

    /** The results of a stored search after the given rank, for the in-session refinement. */
    @Transactional(readOnly = true)
    public TutorRecommendationResponse resultsAfter(RecommendationLog log, int afterRank) {
        return pageOf(log, Math.max(0, afterRank), settings.getLogCandidateLimit(), 0);
    }

    private TutorRecommendationResponse pageOf(RecommendationLog log, int offset, int size, int page) {
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
        Map<UUID, TeachingCandidate> loaded = new HashMap<>();
        candidateRepository.load(log.getSearcherStudent().getId(), hits, settings.getWillingnessHistoryDays())
                .forEach(candidate -> loaded.put(candidate.teachingRequestId(), candidate));
        // A posting deleted since the search simply drops out of the page.
        List<Item> items = slice.stream()
                .filter(node -> loaded.containsKey(UUID.fromString(node.path("candidateId").asString())))
                .map(node -> item(node.path("rank").asInt(),
                        loaded.get(UUID.fromString(node.path("candidateId").asString())),
                        node.path("score").asDouble(), recommendationLogger.reasons(node),
                        node.path("isExploration").asBoolean(false)))
                .collect(Collectors.toList());
        double averageConfidence = stored.stream().limit(CONFIDENCE_SAMPLE)
                .mapToDouble(node -> node.path("confidence").asDouble(1.0)).average().orElse(1.0);
        return new TutorRecommendationResponse(
                log.getId(), log.getAlgorithmVersion(), responseContext(log.getQueryContext()), List.of(),
                averageConfidence < settings.getLowConfidenceThreshold(), items,
                recommendationLogger.relaxations(log.getRelaxationSuggestions()), page, size, stored.size());
    }

    /** The stored context also holds the demand text and the rerank status, which are internal. */
    private JsonNode responseContext(JsonNode stored) {
        if (stored == null || !stored.isObject()) {
            return stored;
        }
        tools.jackson.databind.node.ObjectNode copy = ((tools.jackson.databind.node.ObjectNode) stored).deepCopy();
        copy.remove("demandText");
        copy.remove("rerankStatus");
        return copy;
    }

    private Item item(
            int rank, TeachingCandidate candidate, double score, MatchReasons reasons, boolean exploration) {
        return new Item(
                rank,
                candidate.teachingRequestId(),
                new TutorRecommendationResponse.Tutor(
                        candidate.tutorUserId(), candidate.tutorDisplayName(), avatarUrl(candidate.tutorAvatarUrl()),
                        candidate.tutorAverageRating(), candidate.tutorTotalReviews()),
                candidate.title(),
                candidate.expectedPrice(),
                candidate.teachingMode(),
                candidate.districts().stream().map(TeachingCandidate.District::name).toList(),
                Math.round(score * 1000) / 1000.0,
                exploration,
                reasons == null ? List.of() : reasons.reasons(),
                reasons == null ? null : reasons.caution());
    }

    private String avatarUrl(String value) {
        if (fileStorageService == null || value == null || !value.startsWith("s3://")) {
            return value;
        }
        return fileStorageService.accessUrl(value);
    }
}
