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
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.entity.enums.TeachingMode;

import lombok.RequiredArgsConstructor;

/**
 * Suitability "a" of a teaching posting from the student's point of view (design 06, 6.4-6.5).
 * Pure computation: no database or network access. The behaviour-history feature is added later.
 */
@Component
@RequiredArgsConstructor
public class FeatureCalculator {

    static final double NEED_PARENT_MATCH = 0.6;
    static final double NEED_WITHOUT_TUTOR_TAGS = 0.4;
    static final double STYLE_WITHOUT_TUTOR_TAGS = 0.5;
    static final double SEGMENT_ADJACENT = 0.5;
    static final double SEGMENT_OUTSIDE = 0.1;
    static final double SEGMENT_WITHOUT_TARGET = 0.5;
    static final int SCHEDULE_NEED_CAP_MINUTES = 180;

    /** Levels in order; "can_kem_cap" is treated as the lowest one. */
    private static final Map<String, Integer> LEVEL_ORDER = Map.of(
            "mat_goc", 0, "can_kem_cap", 0, "trung_binh", 1, "kha", 2, "gioi", 3);

    private final RecommendationSettings settings;

    public FeatureVector calculate(QueryContext context, TeachingCandidate candidate) {
        Double sem = semantic(candidate.cosine());
        Double need = need(context.needTags(), candidate.strengthTags());
        Double style = style(context.styleTags(), candidate.offeredStyleCodes());
        Double segment = segment(context.levelTagCode(), candidate.targetSegmentCodes());
        Double price = price(candidate.expectedPrice(), context.maxPrice());
        Integer overlap = overlapMinutes(context.availabilities(), candidate.availabilities());
        Double sched = overlap == null ? null
                : Math.min(1.0, overlap / (double) Math.min(totalMinutes(context.availabilities()),
                        SCHEDULE_NEED_CAP_MINUTES));
        Double area = area(context, candidate);

        double weighted = 0;
        double confidence = 0;
        double[][] parts = {
            { settings.getWeightSem(), value(sem) }, { settings.getWeightNeed(), value(need) },
            { settings.getWeightPrice(), value(price) }, { settings.getWeightStyle(), value(style) },
            { settings.getWeightSched(), value(sched) }, { settings.getWeightArea(), value(area) },
            { settings.getWeightSegment(), value(segment) }
        };
        for (double[] part : parts) {
            if (!Double.isNaN(part[1])) {
                weighted += part[0] * part[1];
                confidence += part[0];
            }
        }
        // Features we could not compute count as neutral instead of being ignored: a posting known only
        // by one perfect feature must not outrank one that is good on everything.
        // Same as  c_a * (weighted mean of known features) + (1 - c_a) * prior.
        double suitability = weighted + (1 - confidence) * settings.getConfidencePrior();
        return new FeatureVector(sem, need, style, segment, price, sched, area,
                suitability, confidence, overlap != null && overlap == 0);
    }

    /** Fixed thresholds instead of per-query min-max, so scores of different searches are comparable. */
    Double semantic(Double cosine) {
        if (cosine == null) {
            return null;
        }
        return clip((cosine - settings.getCosLow()) / (settings.getCosHigh() - settings.getCosLow()));
    }

    /** How much of what the student needs is covered by the tutor's strengths. */
    Double need(List<WeightedTag> needTags, Set<CandidateTag> strengthTags) {
        if (needTags.isEmpty()) {
            return null;
        }
        if (strengthTags.isEmpty()) {
            return NEED_WITHOUT_TUTOR_TAGS;
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
                match = NEED_PARENT_MATCH;
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
            return STYLE_WITHOUT_TUTOR_TAGS;
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
            return SEGMENT_WITHOUT_TARGET;
        }
        int distance = targets.stream().mapToInt(target -> Math.abs(target - level)).min().orElseThrow();
        return distance == 0 ? 1.0 : distance == 1 ? SEGMENT_ADJACENT : SEGMENT_OUTSIDE;
    }

    /** Full marks within budget, then a linear drop to 0 at 1.5 x budget. */
    Double price(BigDecimal expectedPrice, BigDecimal budget) {
        if (expectedPrice == null || budget == null || budget.signum() <= 0) {
            return null;
        }
        double p = expectedPrice.doubleValue();
        double hi = budget.doubleValue();
        return p <= hi ? 1.0 : Math.max(0, 1 - (p - hi) / (0.5 * hi));
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

    private double value(Double feature) {
        return feature == null ? Double.NaN : feature;
    }

    private double clip(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
