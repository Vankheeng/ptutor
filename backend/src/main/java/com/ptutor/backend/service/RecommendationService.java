package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.dto.response.TutorRecommendationResponse;
import com.ptutor.backend.dto.response.TutorRecommendationResponse.Item;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CandidateRepository;
import com.ptutor.backend.repository.CandidateRepository.Hit;
import com.ptutor.backend.repository.RecommendationLogRepository;
import com.ptutor.backend.repository.StudentRepository;

import tools.jackson.databind.JsonNode;

/**
 * "Student looks for tutors", version V2-RCX: merge the criteria, hard-filter OPEN teaching
 * postings, score each one from both sides (how well it suits the student, how likely the tutor
 * is to accept), rerank the best ones with a cross-encoder and return them best first.
 */
@Service
public class RecommendationService {

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
        TutorRecommendationRequest effective = request == null
                ? new TutorRecommendationRequest(null, null, null, null) : request;
        QueryContext context = queryContextBuilder.forStudent(student, effective);
        if (!context.isComplete()) {
            return new TutorRecommendationResponse(
                    null, settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                    context.needsInput(), false, List.of(), List.of(), safePage, safeSize, 0);
        }

        Ranking ranking = rank(student, context);
        RerankService.Result rerank = rerankService.forSearch(
                context.demandText(), RecommendationCandidateType.TEACHING_REQUEST, ids(ranking.ranked()));
        List<ScoredCandidate> kept = ranker
                .applyRerank(context, ranking.ranked(), rerank.scores(), ranking.platformRate())
                .stream().limit(settings.getLogCandidateLimit()).toList();

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        RecommendationLog log = recommendationLogger.logStudentSearch(
                student, context, effective.queryText(), kept, settings.getAlgorithmVersion(), latencyMs,
                rerank.status());

        List<Item> items = new ArrayList<>();
        int from = Math.min(safePage * safeSize, kept.size());
        int to = Math.min(from + safeSize, kept.size());
        for (int index = from; index < to; index++) {
            items.add(item(index + 1, kept.get(index).candidate(), kept.get(index).score()));
        }
        return new TutorRecommendationResponse(
                log.getId(), settings.getAlgorithmVersion(), recommendationLogger.contextJson(context),
                List.of(), isLowConfidence(kept), items, List.of(), safePage, safeSize, kept.size());
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
        QueryContext context = queryContextBuilder.forStudent(
                student, new TutorRecommendationRequest(null, null, null, studyingRequestId));
        if (!context.isComplete()) {
            return 0;
        }
        return rerankService.prefetch(context.demandText(), RecommendationCandidateType.TEACHING_REQUEST,
                ids(rank(student, context).ranked())).computed();
    }

    /** Phases 1-3: hard filter, features of both sides, final score. */
    private Ranking rank(Student student, QueryContext context) {
        // Without a usable query vector the ranking still works, only without the semantic feature.
        float[] queryVector = queryEmbeddingService.embed(context.demandText())
                .orElse(student.getProfileEmbedding());
        List<Hit> hits = selectHits(
                candidateRepository.findHits(context, queryVector, settings.getPriceHardFactor()));
        double platformRate = candidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        return new Ranking(ranker.rank(context,
                candidateRepository.load(student.getId(), hits, settings.getWillingnessHistoryDays()),
                platformRate), platformRate);
    }

    private List<UUID> ids(List<ScoredCandidate> ranked) {
        return ranked.stream().map(scored -> scored.candidate().teachingRequestId()).toList();
    }

    private record Ranking(List<ScoredCandidate> ranked, double platformRate) {
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
        Map<UUID, TeachingCandidate> loaded = new HashMap<>();
        candidateRepository.load(student.getId(), hits, settings.getWillingnessHistoryDays())
                .forEach(candidate -> loaded.put(candidate.teachingRequestId(), candidate));
        // A posting deleted since the search simply drops out of the page.
        List<Item> items = slice.stream()
                .filter(node -> loaded.containsKey(UUID.fromString(node.path("candidateId").asString())))
                .map(node -> item(node.path("rank").asInt(),
                        loaded.get(UUID.fromString(node.path("candidateId").asString())),
                        node.path("score").asDouble()))
                .collect(Collectors.toList());
        double averageConfidence = stored.stream().limit(CONFIDENCE_SAMPLE)
                .mapToDouble(node -> node.path("confidence").asDouble(1.0)).average().orElse(1.0);
        return new TutorRecommendationResponse(
                log.getId(), log.getAlgorithmVersion(), responseContext(log.getQueryContext()), List.of(),
                averageConfidence < settings.getLowConfidenceThreshold(), items, List.of(), page, size,
                stored.size());
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

    private Item item(int rank, TeachingCandidate candidate, double score) {
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
                false,
                List.of(),
                null);
    }

    private String avatarUrl(String value) {
        if (fileStorageService == null || value == null || !value.startsWith("s3://")) {
            return value;
        }
        return fileStorageService.accessUrl(value);
    }
}
