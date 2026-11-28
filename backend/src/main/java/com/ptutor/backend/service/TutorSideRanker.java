package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.History;
import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate.PastProposal;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.LearningMode;

import lombok.RequiredArgsConstructor;

/**
 * Scores studying postings for a tutor (design 06, 6.12). Pure computation.
 * a' is how well the posting suits the tutor, b' how likely the student is to accept the tutor,
 * R' their harmonic mean and {@code score' = R' x G'}: students have no quality factor.
 */
@Component
@RequiredArgsConstructor
public class TutorSideRanker {

    private static final Comparator<ScoredStudyingCandidate> ORDER = Comparator
            .comparingDouble(ScoredStudyingCandidate::score).reversed()
            .thenComparing(scored -> scored.candidate().createdAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(scored -> scored.candidate().studyingRequestId());

    private final FeatureCalculator featureCalculator;
    private final RecommendationSettings settings;
    private final WeightProvider weightProvider;

    /** @param platformRate share of tutor proposals that students accept, platform-wide */
    public List<ScoredStudyingCandidate> rank(
            SupplyContext context, List<StudyingCandidate> candidates, double platformRate) {
        return rank(context, candidates, platformRate, History.NONE);
    }

    /** @param history what the tutor's preference profile says about these candidates */
    public List<ScoredStudyingCandidate> rank(
            SupplyContext context, List<StudyingCandidate> candidates, double platformRate, History history) {
        return candidates.stream()
                .map(candidate -> score(context, candidate, platformRate, null,
                        history.of(candidate.studyingRequestId()), history.weight()))
                .sorted(ORDER).toList();
    }

    /** Phase 4, as in {@link Ranker#applyRerank}: only the top group is scored and sorted again. */
    public List<ScoredStudyingCandidate> applyRerank(
            SupplyContext context, List<ScoredStudyingCandidate> ranked, Map<UUID, Double> rerankScores,
            double platformRate) {
        if (rerankScores.isEmpty()) {
            return ranked;
        }
        int top = Math.min(settings.getRerankTopK(), ranked.size());
        List<ScoredStudyingCandidate> result = new ArrayList<>(ranked.subList(0, top).stream()
                .map(scored -> {
                    Double rerank = rerankScores.get(scored.candidate().studyingRequestId());
                    return rerank == null ? scored : score(context, scored.candidate(), platformRate, rerank,
                            scored.features().hist(), scored.features().histWeight());
                })
                .sorted(ORDER).toList());
        result.addAll(ranked.subList(top, ranked.size()));
        return result;
    }

    ScoredStudyingCandidate score(SupplyContext context, StudyingCandidate candidate, double platformRate) {
        return score(context, candidate, platformRate, null, null, 0);
    }

    ScoredStudyingCandidate score(
            SupplyContext context, StudyingCandidate candidate, double platformRate, Double rerank,
            Double hist, double histWeight) {
        FeatureVector features = features(context, candidate, rerank, hist, histWeight);
        double a = features.suitability();
        double g = (features.noScheduleOverlap() ? settings.getGateNoScheduleOverlap() : 1.0)
                * (candidate.rejectedTutorRecently() ? settings.getGateRecentReject() : 1.0);
        Willingness willingness = settings.isReciprocalEnabled()
                ? willingness(context, candidate, platformRate) : null;
        double r = willingness == null ? a
                : Ranker.reciprocal(a, willingness.b(), settings.getReciprocalMode(), settings.getReciprocalAlpha());
        return new ScoredStudyingCandidate(candidate, features, a, willingness, r, g, r * g);
    }

    /** a': suitability from the tutor's point of view. The style feature belongs to b', not here. */
    FeatureVector features(SupplyContext context, StudyingCandidate candidate) {
        return features(context, candidate, null, null, 0);
    }

    FeatureVector features(
            SupplyContext context, StudyingCandidate candidate, Double rerank, Double hist, double histWeight) {
        Double sem = featureCalculator.semantic(candidate.cosine(), rerank);
        Double price = priceFromTutorView(candidate.maxPrice(), context.expectedPrice());
        Double need = featureCalculator.need(candidate.needTags(), context.strengthTags());
        Double segment = featureCalculator.segment(candidate.levelCode(), context.targetSegmentCodes());
        Integer overlap = featureCalculator.overlapMinutes(context.availabilities(), candidate.availabilities());
        Double sched = overlap == null ? null : Math.min(1.0, overlap / (double) Math.min(
                featureCalculator.overlapMinutes(context.availabilities(), context.availabilities()),
                settings.getScheduleNeedCapMinutes()));
        Double area = area(context, candidate);

        // In the order of WeightModel.A_T2S.
        Double[] features = { sem, price, need, segment, sched, area };
        double[] suitability = featureCalculator.suitability(
                weightProvider.weightsFor(WeightModel.A_T2S, context.tutorUserId()), features);
        return new FeatureVector(sem, rerank, need, null, segment, price, sched, area, hist,
                hist == null ? 0 : histWeight, FeatureCalculator.blend(suitability[0], hist, histWeight),
                suitability[1], overlap != null && overlap == 0);
    }

    /** b': the student's side. */
    Willingness willingness(SupplyContext context, StudyingCandidate candidate, double platformRate) {
        double history = history(context, candidate, platformRate);
        ModelWeights weights = weightProvider.weights(WeightModel.B_T2S);
        double[] parts = fitParts(context, candidate);
        double fit = WillingnessCalculator.fit(weights, parts);
        double capacity = settings.isCongestionEnabled()
                ? WillingnessCalculator.congestion(candidate.remainingSlots(), candidate.pendingProposals(),
                        settings.getCongestionLambda(), settings.getCongestionFormula())
                : 1.0;
        if (candidate.studentAppliedToTutor()) {
            return new Willingness(1.0, history, fit, capacity, true, WillingnessCalculator.asList(parts));
        }
        // Flattened form (design 12, 12.2): with the prior weights, 0.5 x b'_hist + 0.5 x b'_fit.
        return new Willingness((weights.get(0) * history + WillingnessCalculator.fitShare(weights) * fit) * capacity,
                history, fit, capacity, false, WillingnessCalculator.asList(parts));
    }

    /** Acceptance rate of the student's past decisions, weighted by how similar the proposing tutor was. */
    double history(SupplyContext context, StudyingCandidate candidate, double platformRate) {
        double accepted = 0;
        double total = 0;
        for (PastProposal proposal : candidate.studentDecisions()) {
            double priceSimilarity = context.expectedPrice() == null || proposal.proposedPrice() == null
                    || proposal.proposedPrice().signum() <= 0
                    ? settings.getTutorWillingnessNeutral()
                    : 1 - Math.min(1, Math.abs(context.expectedPrice().doubleValue()
                            - proposal.proposedPrice().doubleValue()) / proposal.proposedPrice().doubleValue());
            double qualitySimilarity = proposal.tutorQuality() == null
                    ? settings.getTutorWillingnessNeutral()
                    : 1 - Math.abs(context.quality() - proposal.tutorQuality());
            double similarity = settings.getTutorWillingnessKernelPrice() * priceSimilarity
                    + settings.getTutorWillingnessKernelQuality() * qualitySimilarity;
            total += similarity;
            if (proposal.accepted()) {
                accepted += similarity;
            }
        }
        double alpha = settings.getWillingnessAlpha();
        return (accepted + alpha * platformRate) / (total + alpha);
    }

    /**
     * What the student sees in the tutor, in the order of {@link WeightModel#B_T2S}: quality, teaching style and
     * price. A part without data is neutral (0.5), unlike b_fit, so the ranking stays as it was (design 12, 12.2).
     */
    double[] fitParts(SupplyContext context, StudyingCandidate candidate) {
        Double style = style(candidate.preferredStyleCodes(), context.offeredStyleCodes());
        Double price = priceFromStudentView(context.expectedPrice(), candidate.maxPrice());
        double neutral = settings.getTutorWillingnessNeutral();
        return new double[] { context.quality(), style == null ? neutral : style, price == null ? neutral : price };
    }

    /** b'_fit: the weighted mean of {@link #fitParts}. */
    double fit(SupplyContext context, StudyingCandidate candidate) {
        return WillingnessCalculator.fit(weightProvider.weights(WeightModel.B_T2S), fitParts(context, candidate));
    }

    /** Full marks when the student's budget covers the price the tutor hopes for. */
    Double priceFromTutorView(BigDecimal budget, BigDecimal expectedPrice) {
        if (budget == null || expectedPrice == null || expectedPrice.signum() <= 0) {
            return null;
        }
        double hi = budget.doubleValue();
        double p = expectedPrice.doubleValue();
        return hi >= p ? 1.0 : Math.max(0, 1 - (p - hi) / (settings.getTutorPriceTolerance() * p));
    }

    Double priceFromStudentView(BigDecimal expectedPrice, BigDecimal budget) {
        if (budget == null || expectedPrice == null || budget.signum() <= 0) {
            return null;
        }
        double hi = budget.doubleValue();
        double p = expectedPrice.doubleValue();
        return p <= hi ? 1.0 : Math.max(0, 1 - (p - hi) / (settings.getPriceOverBudgetRange() * hi));
    }

    private Double style(Set<String> wanted, Set<String> offered) {
        if (wanted.isEmpty()) {
            return null;
        }
        if (offered.isEmpty()) {
            return settings.getStyleWithoutTutorTags();
        }
        return wanted.stream().filter(offered::contains).count() / (double) wanted.size();
    }

    /** Where the student wants to learn, compared with where the tutor said they can teach. */
    private Double area(SupplyContext context, StudyingCandidate candidate) {
        if (candidate.learningMode() == LearningMode.ONLINE) {
            return 1.0;
        }
        if (candidate.district() == null) {
            return null;
        }
        Set<UUID> preferred = context.areaScope() == AreaScope.DISTRICTS
                ? Set.copyOf(context.districtIds())
                : context.homeDistrictId() == null ? Set.of() : Set.of(context.homeDistrictId());
        UUID referenceProvince = context.provinceId() != null ? context.provinceId() : context.homeProvinceId();
        if (preferred.isEmpty() && referenceProvince == null) {
            return null;
        }
        if (preferred.contains(candidate.district().id())) {
            return 1.0;
        }
        return referenceProvince != null && referenceProvince.equals(candidate.district().provinceId())
                ? settings.getAreaSameProvince() : settings.getAreaOtherProvince();
    }
}
