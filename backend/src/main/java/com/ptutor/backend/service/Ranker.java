package com.ptutor.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;

import lombok.RequiredArgsConstructor;

/**
 * Final score of version V2-a (design 06, 6.8): {@code score = a x (0.7 + 0.3 Q) x G}. Quality
 * only shifts the score by about 15 %, and the gate multiplies instead of adding, so a good
 * semantic match cannot compensate for a schedule that never overlaps.
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
    private final RecommendationSettings settings;

    public List<ScoredCandidate> rank(QueryContext context, List<TeachingCandidate> candidates) {
        return candidates.stream().map(candidate -> score(context, candidate)).sorted(ORDER).toList();
    }

    ScoredCandidate score(QueryContext context, TeachingCandidate candidate) {
        FeatureVector features = featureCalculator.calculate(context, candidate);
        double a = features.suitability();
        double q = candidate.tutorScore() == null
                ? QUALITY_WHEN_UNKNOWN
                : Math.max(0, Math.min(1, candidate.tutorScore().doubleValue() / 100.0));
        double g = (features.noScheduleOverlap() ? settings.getGateNoScheduleOverlap() : 1.0)
                * (candidate.rejectedStudentRecently() ? settings.getGateRecentReject() : 1.0);
        double score = a * (settings.getQualityBase() + settings.getQualityWeight() * q) * g;
        return new ScoredCandidate(candidate, features, a, q, g, score);
    }
}
