package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.TeachingMode;

import lombok.RequiredArgsConstructor;

/**
 * Suitability "a" of a teaching posting from the student's point of view (design 06, 6.4-6.5).
 * Pure computation: no database or network access. The behaviour-history feature is added later.
 */
@Component
@RequiredArgsConstructor
public class FeatureCalculator {

    /** Levels in order; "can_kem_cap" is treated as the lowest one. */
    private static final Map<String, Integer> LEVEL_ORDER = Map.of(
            "mat_goc", 0, "can_kem_cap", 0, "trung_binh", 1, "kha", 2, "gioi", 3);

    private final RecommendationSettings settings;
    private final WeightProvider weightProvider;

    public FeatureVector calculate(QueryContext context, TeachingCandidate candidate) {
        return calculate(context, candidate, null);
    }

    /** @param rerank cross-encoder score of the posting, or null when it has none (design 06, 6.9) */
    public FeatureVector calculate(QueryContext context, TeachingCandidate candidate, Double rerank) {
        return calculate(context, candidate, rerank, null, 0);
    }

    /**
     * @param hist       f_hist of the posting, or null when the student has no preference profile
     * @param histWeight w_hist, see {@link #blend}
     */
    public FeatureVector calculate(
            QueryContext context, TeachingCandidate candidate, Double rerank, Double hist, double histWeight) {
        Double sem = semantic(candidate.cosine(), rerank);
        Double need = need(context.needTags(), candidate.strengthTags());
        Double style = style(context.styleTags(), candidate.offeredStyleCodes());
        Double segment = segment(context.levelTagCode(), candidate.targetSegmentCodes());
        Double price = price(candidate.expectedPrice(), context.maxPrice());
        Integer overlap = overlapMinutes(context.availabilities(), candidate.availabilities());
        Double sched = overlap == null ? null
                : Math.min(1.0, overlap / (double) Math.min(totalMinutes(context.availabilities()),
                        settings.getScheduleNeedCapMinutes()));
        Double area = area(context, candidate);

        // In the order of WeightModel.A_S2T.
        Double[] features = { sem, need, price, style, sched, area, segment };
        double[] suitability = suitability(
                weightProvider.weightsFor(WeightModel.A_S2T, context.studentUserId()), features);
        return new FeatureVector(sem, rerank, need, style, segment, price, sched, area, hist,
                hist == null ? 0 : histWeight, blend(suitability[0], hist, histWeight), suitability[1],
                overlap != null && overlap == 0);
    }

    /**
     * {@code a_base = Σ w_k × x_k}, where a feature we could not compute counts as the neutral prior instead of
     * being ignored: a posting known only by one perfect feature must not outrank one that is good on
     * everything (design 06, 6.5; 12, 12.2). With weights summing to 1 this is
     * {@code c_a × (weighted mean of known features) + (1 - c_a) × prior}.
     *
     * @param features in the order of the model's features, null when unknown
     * @return {a_base, c_a}: the suitability and the share of the weight on known features
     */
    double[] suitability(ModelWeights weights, Double[] features) {
        double suitability = 0;
        double confidence = 0;
        for (int i = 0; i < features.length; i++) {
            double weight = weights.get(i);
            if (features[i] == null) {
                suitability += weight * settings.getConfidencePrior();
            } else {
                suitability += weight * features[i];
                confidence += weight;
            }
        }
        return new double[] { suitability, confidence };
    }

    /**
     * {@code a = (1 - w_hist) x a_base + w_hist x f_hist} (design 06, 6.4): history never replaces the
     * criteria of the search, it only shifts the result by up to 15 % once there is enough of it.
     */
    static double blend(double base, Double hist, double histWeight) {
        return hist == null || histWeight <= 0 ? base : (1 - histWeight) * base + histWeight * hist;
    }

    /** Fixed thresholds instead of per-query min-max, so scores of different searches are comparable. */
    Double semantic(Double cosine) {
        if (cosine == null) {
            return null;
        }
        return clip((cosine - settings.getCosLow()) / (settings.getCosHigh() - settings.getCosLow()));
    }

    /**
     * {@code f_sem = (1 - w) x s + w x rerank} when the posting was reranked, the cosine part alone
     * otherwise. A reranked posting that has no vector yet is judged by the cross-encoder alone.
     */
    Double semantic(Double cosine, Double rerank) {
        Double s = semantic(cosine);
        if (rerank == null) {
            return s;
        }
        double weight = settings.getRerankWeight();
        return s == null ? clip(rerank) : clip((1 - weight) * s + weight * rerank);
    }

    /** How much of what the student needs is covered by the tutor's strengths. */
    Double need(List<WeightedTag> needTags, Set<CandidateTag> strengthTags) {
        if (needTags.isEmpty()) {
            return null;
        }
        if (strengthTags.isEmpty()) {
            return settings.getNeedWithoutTutorTags();
        }
        Set<UUID> strengthIds = strengthTags.stream().map(CandidateTag::tagId).collect(Collectors.toSet());
        Set<UUID> strengthParentIds = strengthTags.stream()
                .map(CandidateTag::parentId).filter(id -> id != null).collect(Collectors.toSet());
        double matched = 0;
        double total = 0;
        for (WeightedTag tag : needTags) {
            double match;
            if (strengthIds.contains(tag.tagId())) {
                match = 1.0;
            } else if ((tag.parentId() != null && strengthIds.contains(tag.parentId()))
                    || strengthParentIds.contains(tag.tagId())) {
                match = settings.getNeedParentMatch();
            } else {
                match = 0;
            }
            matched += tag.confidence() * match;
            total += tag.confidence();
        }
        return total == 0 ? null : matched / total;
    }

    Double style(List<WeightedTag> wanted, Set<String> offered) {
        if (wanted.isEmpty()) {
            return null;
        }
        if (offered.isEmpty()) {
            return settings.getStyleWithoutTutorTags();
        }
        long shared = wanted.stream().map(WeightedTag::code).distinct().filter(offered::contains).count();
        return shared / (double) wanted.stream().map(WeightedTag::code).distinct().count();
    }

    Double segment(String studentLevel, Set<String> targetSegments) {
        Integer level = studentLevel == null ? null : LEVEL_ORDER.get(studentLevel);
        if (level == null) {
            return null;
        }
        List<Integer> targets = targetSegments.stream()
                .map(LEVEL_ORDER::get).filter(value -> value != null).toList();
        if (targets.isEmpty()) {
            return settings.getSegmentWithoutTarget();
        }
        int distance = targets.stream().mapToInt(target -> Math.abs(target - level)).min().orElseThrow();
        return distance == 0 ? 1.0 : distance == 1 ? settings.getSegmentAdjacent() : settings.getSegmentOutside();
    }

    /** Full marks within budget, then a linear drop to 0 at (1 + over-budget-range) x budget. */
    Double price(BigDecimal expectedPrice, BigDecimal budget) {
        if (expectedPrice == null || budget == null || budget.signum() <= 0) {
            return null;
        }
        double p = expectedPrice.doubleValue();
        double hi = budget.doubleValue();
        return p <= hi ? 1.0 : Math.max(0, 1 - (p - hi) / (settings.getPriceOverBudgetRange() * hi));
    }

    /** Shared minutes per week; null when either side gave no schedule. */
    Integer overlapMinutes(List<TimeSlot> wanted, List<TimeSlot> offered) {
        if (wanted.isEmpty() || offered.isEmpty()) {
            return null;
        }
        int minutes = 0;
        for (int day = 1; day <= 7; day++) {
            boolean[] wantedMinutes = minutesOfDay(wanted, day);
            boolean[] offeredMinutes = minutesOfDay(offered, day);
            for (int minute = 0; minute < wantedMinutes.length; minute++) {
                if (wantedMinutes[minute] && offeredMinutes[minute]) {
                    minutes++;
                }
            }
        }
        return minutes;
    }

    Double area(QueryContext context, TeachingCandidate candidate) {
        if (candidate.teachingMode() == TeachingMode.ONLINE) {
            return 1.0;
        }
        Set<UUID> preferred = context.areaScope() == AreaScope.DISTRICTS
                ? Set.copyOf(context.districtIds())
                : context.homeDistrictId() == null ? Set.of() : Set.of(context.homeDistrictId());
        UUID referenceProvince = context.provinceId() != null ? context.provinceId() : context.homeProvinceId();
        if (preferred.isEmpty() && referenceProvince == null) {
            return null;
        }
        if (candidate.districts().stream().anyMatch(district -> preferred.contains(district.id()))) {
            return 1.0;
        }
        if (referenceProvince != null && candidate.districts().stream()
                .anyMatch(district -> referenceProvince.equals(district.provinceId()))) {
            return settings.getAreaSameProvince();
        }
        return settings.getAreaOtherProvince();
    }

    private int totalMinutes(List<TimeSlot> slots) {
        int minutes = 0;
        for (int day = 1; day <= 7; day++) {
            for (boolean busy : minutesOfDay(slots, day)) {
                if (busy) {
                    minutes++;
                }
            }
        }
        return minutes;
    }

    /** Minute-by-minute mask, so overlapping slots of the same person are not counted twice. */
    private boolean[] minutesOfDay(List<TimeSlot> slots, int day) {
        boolean[] minutes = new boolean[(int) Duration.ofDays(1).toMinutes()];
        for (TimeSlot slot : slots) {
            if (slot.dayOfWeek() == day) {
                int from = slot.startTime().toSecondOfDay() / 60;
                int to = slot.endTime().toSecondOfDay() / 60;
                for (int minute = from; minute < to; minute++) {
                    minutes[minute] = true;
                }
            }
        }
        return minutes;
    }

    private double clip(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
