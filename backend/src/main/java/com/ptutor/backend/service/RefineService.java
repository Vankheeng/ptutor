package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.RecommendationClickLog;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.repository.PreferenceSignalRepository;
import com.ptutor.backend.repository.RecommendationClickLogRepository;
import com.ptutor.backend.repository.RecommendationLogRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * B5 (design 07, 7.2): while the user looks through the results of one search, what they open and
 * what they dismiss says more about what they want than the filters did. The query vector is moved
 * towards the results they liked and away from the ones they did not (Rocchio), and the results
 * they have not seen yet are scored again. What is already on screen never moves.
 */
@Service
@RequiredArgsConstructor
public class RefineService {

    static final Set<RecommendationAction> POSITIVE = EnumSet.of(RecommendationAction.VIEWED,
            RecommendationAction.CLICKED, RecommendationAction.FAVORITED, RecommendationAction.CONTACTED);

    private final RecommendationEventService eventService;
    private final RecommendationClickLogRepository clickLogRepository;
    private final RecommendationLogRepository logRepository;
    private final PreferenceSignalRepository signalRepository;
    private final QueryEmbeddingService queryEmbeddingService;
    private final RecommendationService recommendationService;
    private final StudentRecommendationService studentRecommendationService;
    private final RecommendationSettings settings;
    private final ObjectMapper objectMapper;

    /**
     * @return the results after {@code seenUpToRank} in their new order: a TutorRecommendationResponse
     *         for a student's search, a StudentRecommendationResponse for a tutor's
     */
    @Transactional
    public Object refine(UUID userId, UUID logId, int seenUpToRank) {
        RecommendationLog log = eventService.ownedLog(userId, logId);
        boolean studentSearch = log.getDirection() == RecommendationDirection.STUDENT_TO_TUTOR;
        if (settings.isRefineEnabled()) {
            reorder(log, studentSearch, seenUpToRank).ifPresent(reordered -> {
                log.setReturnedCandidateIds(reordered);
                logRepository.saveAndFlush(log);
            });
        }
        return studentSearch ? recommendationService.resultsAfter(log, seenUpToRank)
                : studentRecommendationService.resultsAfter(log, seenUpToRank);
    }

    /** Empty when there is nothing to learn from yet (no positive signal) or nothing left to reorder. */
    private Optional<ArrayNode> reorder(RecommendationLog log, boolean studentSearch, int seenUpToRank) {
        List<ObjectNode> stored = new ArrayList<>();
        if (log.getReturnedCandidateIds() != null) {
            log.getReturnedCandidateIds().forEach(node -> stored.add(((ObjectNode) node).deepCopy()));
        }
        if (stored.size() <= seenUpToRank) {
            return Optional.empty();
        }

        Set<UUID> liked = new HashSet<>();
        Set<UUID> dismissed = new HashSet<>();
        for (RecommendationClickLog click : clickLogRepository.findAllByRecommendationLog_Id(log.getId())) {
            UUID candidateId = click.getCandidateTeachingRequest() != null ? click.getCandidateTeachingRequest().getId()
                    : click.getCandidateStudyingRequest() != null ? click.getCandidateStudyingRequest().getId() : null;
            if (candidateId == null) {
                continue;
            }
            if (click.getAction() == RecommendationAction.DISMISSED) {
                dismissed.add(candidateId);
            } else if (POSITIVE.contains(click.getAction())) {
                liked.add(candidateId);
            }
        }
        liked.removeAll(dismissed);
        if (liked.isEmpty()) {
            return Optional.empty();
        }

        JsonNode context = log.getQueryContext();
        String text = context == null ? "" : context.path(studentSearch ? "demandText" : "supplyText").asString("");
        // Normally answered from the in-memory cache of the search that produced this log.
        float[] query = queryEmbeddingService.embed(text).orElse(null);
        if (query == null) {
            return Optional.empty();
        }
        RecommendationCandidateType type = studentSearch
                ? RecommendationCandidateType.TEACHING_REQUEST : RecommendationCandidateType.STUDYING_REQUEST;
        Map<UUID, float[]> vectors = signalRepository.embeddings(type, stored.stream().map(this::id).toList());

        // Passed over: shown above something the user opened, but neither opened nor dismissed itself.
        int lowestLikedRank = stored.stream().filter(node -> liked.contains(id(node)))
                .mapToInt(node -> node.path("rank").asInt()).max().orElse(0);
        Map<UUID, Double> negative = new HashMap<>();
        for (ObjectNode node : stored) {
            UUID candidateId = id(node);
            if (dismissed.contains(candidateId)) {
                negative.put(candidateId, 1.0);
            } else if (!liked.contains(candidateId) && node.path("rank").asInt() < lowestLikedRank) {
                negative.put(candidateId, settings.getRefineSkippedWeight());
            }
        }
        Map<UUID, Double> positive = new HashMap<>();
        liked.forEach(candidateId -> positive.put(candidateId, 1.0));
        float[] moved = rocchio(query, mean(positive, vectors), mean(negative, vectors));

        double weightSem = studentSearch ? settings.getWeightSem() : settings.getTutorWeightSem();
        double range = settings.getCosHigh() - settings.getCosLow();
        List<ObjectNode> unseen = new ArrayList<>(stored.subList(seenUpToRank, stored.size()));
        for (ObjectNode node : unseen) {
            float[] vector = vectors.get(id(node));
            ObjectNode features = node.path("features").isObject() ? (ObjectNode) node.get("features") : null;
            if (vector == null || features == null) {
                continue;
            }
            // Always start from the values of the original search, so refining twice does not compound.
            ObjectNode initial = node.path("initial").isObject() ? (ObjectNode) node.get("initial") : null;
            JsonNode storedSem = initial == null ? features.path("sem") : initial.path("sem");
            double initialA = initial == null ? node.path("a").asDouble() : initial.path("a").asDouble();
            if (!storedSem.isNumber()) {
                continue;
            }
            double initialSem = storedSem.doubleValue();
            if (initial == null) {
                node.putObject("initial").put("sem", initialSem).put("a", initialA);
            }
            double fromSession = clip((dot(moved, vector) - settings.getCosLow()) / range);
            double share = settings.getRefineOriginalShare();
            double sem = share * initialSem + (1 - share) * fromSession;
            double a = clip(initialA + (1 - node.path("wHist").asDouble(0)) * weightSem * (sem - initialSem));
            JsonNode b = node.path("b");
            double r = b.isNumber() ? Ranker.reciprocal(a, b.doubleValue()) : a;
            JsonNode q = node.path("q");
            double quality = q.isNumber() ? settings.getQualityBase() + settings.getQualityWeight() * q.doubleValue() : 1.0;
            features.put("sem", round(sem));
            node.put("a", round(a));
            node.put("r", round(r));
            node.put("score", round(r * quality * node.path("g").asDouble(1.0)));
        }
        unseen.sort(Comparator.comparingDouble((ObjectNode node) -> node.path("score").asDouble()).reversed());

        ArrayNode reordered = objectMapper.createArrayNode();
        int rank = 1;
        for (ObjectNode node : stored.subList(0, seenUpToRank)) {
            node.put("rank", rank++);
            reordered.add(node);
        }
        for (ObjectNode node : unseen) {
            node.put("rank", rank++);
            reordered.add(node);
        }
        return Optional.of(reordered);
    }

    /** {@code q' = normalize(q + positive x mean(liked) - negative x mean(disliked))}. */
    float[] rocchio(float[] query, float[] liked, float[] disliked) {
        double[] sum = new double[query.length];
        double norm = 0;
        for (int index = 0; index < query.length; index++) {
            sum[index] = query[index]
                    + (liked == null ? 0 : settings.getRefinePositiveWeight() * liked[index])
                    - (disliked == null ? 0 : settings.getRefineNegativeWeight() * disliked[index]);
            norm += sum[index] * sum[index];
        }
        norm = Math.sqrt(norm);
        float[] moved = new float[query.length];
        for (int index = 0; index < query.length; index++) {
            moved[index] = norm < 1e-9 ? query[index] : (float) (sum[index] / norm);
        }
        return moved;
    }

    /** Weighted mean of the vectors of the given postings; null when none of them has a vector. */
    private float[] mean(Map<UUID, Double> weights, Map<UUID, float[]> vectors) {
        double[] sum = null;
        double total = 0;
        for (Map.Entry<UUID, Double> entry : weights.entrySet()) {
            float[] vector = vectors.get(entry.getKey());
            if (vector == null) {
                continue;
            }
            sum = sum == null ? new double[vector.length] : sum;
            for (int index = 0; index < vector.length; index++) {
                sum[index] += entry.getValue() * vector[index];
            }
            total += entry.getValue();
        }
        if (sum == null || total <= 0) {
            return null;
        }
        float[] mean = new float[sum.length];
        for (int index = 0; index < sum.length; index++) {
            mean[index] = (float) (sum[index] / total);
        }
        return mean;
    }

    /** Both vectors have length 1, so the dot product is the cosine. */
    private double dot(float[] left, float[] right) {
        double sum = 0;
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            sum += left[index] * right[index];
        }
        return sum;
    }

    private UUID id(JsonNode node) {
        return UUID.fromString(node.path("candidateId").asString());
    }

    private double clip(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
