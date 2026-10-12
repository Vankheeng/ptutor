package com.ptutor.backend.service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.Willingness;

import lombok.RequiredArgsConstructor;

/**
 * Final score (design 06, 6.7-6.8): {@code R = 2ab / (a + b)} and {@code score = R x (0.7 + 0.3 Q) x G}.
 * The harmonic mean is pulled towards the smaller side, so a posting must suit the student and the
 * tutor must be likely to accept. Quality only shifts the score by about 15 %, and the gate
 * multiplies instead of adding, so nothing can compensate for a schedule that never overlaps.
 */
@Component
@RequiredArgsConstructor
public class Ranker {

    static final double QUALITY_WHEN_UNKNOWN = 0.5;

    private static final Comparator<ScoredCandidate> ORDER = Comparator
            .comparingDouble(ScoredCandidate::score).reversed()
            .thenComparing(scored -> scored.candidate().createdAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(scored -> scored.candidate().teachingRequestId());

    private final FeatureCalculator featureCalculator;
    private final WillingnessCalculator willingnessCalculator;
    private final RecommendationSettings settings;

    /** @param platformRate acceptance rate of the whole platform, see {@link WillingnessCalculator} */
    public List<ScoredCandidate> rank(
            QueryContext context, List<TeachingCandidate> candidates, double platformRate) {
        return candidates.stream()
                .map(candidate -> score(context, candidate, platformRate)).sorted(ORDER).toList();
    }

    /**
     * Phase 4 (design 06, 6.9): postings of the top group that have a cross-encoder score are scored
     * again with it and the top group is sorted again. Everything below keeps its place.
     */
    public List<ScoredCandidate> applyRerank(
            QueryContext context, List<ScoredCandidate> ranked, Map<UUID, Double> rerankScores,
            double platformRate) {
        if (rerankScores.isEmpty()) {
            return ranked;
        }
        int top = Math.min(settings.getRerankTopK(), ranked.size());
        List<ScoredCandidate> result = new ArrayList<>(ranked.subList(0, top).stream()
                .map(scored -> {
                    Double rerank = rerankScores.get(scored.candidate().teachingRequestId());
                    return rerank == null ? scored : score(context, scored.candidate(), platformRate, rerank);
                })
                .sorted(ORDER).toList());
        result.addAll(ranked.subList(top, ranked.size()));
        return result;
    }

    ScoredCandidate score(QueryContext context, TeachingCandidate candidate, double platformRate) {
        return score(context, candidate, platformRate, null);
    }

    ScoredCandidate score(QueryContext context, TeachingCandidate candidate, double platformRate, Double rerank) {
        FeatureVector features = featureCalculator.calculate(context, candidate, rerank);
        double a = features.suitability();
        double q = candidate.tutorScore() == null
                ? QUALITY_WHEN_UNKNOWN
                : Math.max(0, Math.min(1, candidate.tutorScore().doubleValue() / 100.0));
        double g = (features.noScheduleOverlap() ? settings.getGateNoScheduleOverlap() : 1.0)
                * (candidate.rejectedStudentRecently() ? settings.getGateRecentReject() : 1.0);
        Willingness willingness = settings.isReciprocalEnabled()
                ? willingnessCalculator.calculate(context, candidate, features, platformRate) : null;
        double r = willingness == null ? a : reciprocal(a, willingness.b());
        double score = r * (settings.getQualityBase() + settings.getQualityWeight() * q) * g;
        return new ScoredCandidate(candidate, features, a, willingness, r, q, g, score);
    }

    static double reciprocal(double a, double b) {
        return a + b == 0 ? 0 : 2 * a * b / (a + b);
    }
}
