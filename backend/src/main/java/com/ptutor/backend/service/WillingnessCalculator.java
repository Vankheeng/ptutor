package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.LearningMode;

import lombok.RequiredArgsConstructor;

/**
 * Willingness "b" of the tutor to take this student (design 06, 6.6). Pure computation.
 * It combines what the tutor decided for similar students before, how well the student fits what
 * the tutor publicly asks for, and how crowded the posting already is.
 */
@Component
@RequiredArgsConstructor
public class WillingnessCalculator {

    static final double WEIGHT_GRADE = 0.5;
    static final double WEIGHT_PRICE = 0.3;
    static final double WEIGHT_MODE = 0.2;
    /** Used for a similarity component when one of the two values is unknown. */
    static final double UNKNOWN_SIMILARITY = 0.5;
    static final double TUTOR_PRICE_TOLERANCE = 0.3;

    private final RecommendationSettings settings;

    /**
     * @param features    suitability features of the same pair (the level and schedule parts are reused)
     * @param platformRate acceptance rate of the whole platform, the prior for tutors with little history
     */
    public Willingness calculate(
            QueryContext context, TeachingCandidate candidate, FeatureVector features, double platformRate) {
        double history = history(context, candidate, platformRate);
        double fit = fit(context, candidate, features, platformRate);
        double capacity = settings.isCongestionEnabled()
                ? capacity(candidate.remainingSlots(), candidate.pendingApplications()) : 1.0;
        if (candidate.tutorInvitedStudent()) {
            return new Willingness(1.0, history, fit, capacity, true);
        }
        return new Willingness((0.5 * history + 0.5 * fit) * capacity, history, fit, capacity, false);
    }

    /**
     * Kernel-weighted acceptance rate over the tutor's past decisions: decisions about students
     * similar to this one count more. Smoothed towards the platform rate, so a tutor with no
     * history gets exactly that rate.
     */
    double history(QueryContext context, TeachingCandidate candidate, double platformRate) {
        BigDecimal price = context.maxPrice() != null ? context.maxPrice() : candidate.expectedPrice();
        double accepted = 0;
        double total = 0;
        for (PastDecision decision : candidate.tutorDecisions()) {
            double similarity = WEIGHT_GRADE * gradeSimilarity(context.gradeLevel(), decision.gradeLevel())
                    + WEIGHT_PRICE * priceSimilarity(price, decision.proposedPrice())
                    + WEIGHT_MODE * modeSimilarity(context.learningMode(), decision.learningMode());
            total += similarity;
            if (decision.accepted()) {
                accepted += similarity;
            }
        }
        double alpha = settings.getWillingnessAlpha();
        return (accepted + alpha * platformRate) / (total + alpha);
    }

    /** Mean of the parts we have data for; the platform rate when we have none. */
    double fit(QueryContext context, TeachingCandidate candidate, FeatureVector features, double platformRate) {
        List<Double> parts = new ArrayList<>();
        if (features.segment() != null) {
            parts.add(features.segment());
        }
        if (features.sched() != null) {
            parts.add(features.sched());
        }
        Double price = priceFromTutorView(context.maxPrice(), candidate.expectedPrice());
        if (price != null) {
            parts.add(price);
        }
        return parts.isEmpty() ? platformRate
                : parts.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** The more applications already wait for the few remaining places, the lower the chance. */
    double capacity(int remainingSlots, int pendingApplications) {
        double remaining = Math.max(1, remainingSlots);
        return remaining / (remaining + settings.getCongestionLambda() * Math.max(0, pendingApplications));
    }

    /** A tutor is happy with a budget at or above the asking price, and unlikely to go 30 % below it. */
    Double priceFromTutorView(BigDecimal budget, BigDecimal askingPrice) {
        if (budget == null || askingPrice == null || askingPrice.signum() <= 0) {
            return null;
        }
        double hi = budget.doubleValue();
        double p = askingPrice.doubleValue();
        return hi >= p ? 1.0 : Math.max(0, 1 - (p - hi) / (TUTOR_PRICE_TOLERANCE * p));
    }

    private double gradeSimilarity(Integer level, Integer pastLevel) {
        if (level == null || pastLevel == null) {
            return UNKNOWN_SIMILARITY;
        }
        int distance = Math.abs(level - pastLevel);
        return distance == 0 ? 1.0 : distance == 1 ? 0.5 : 0.0;
    }

    private double priceSimilarity(BigDecimal price, BigDecimal pastPrice) {
        if (price == null || pastPrice == null || pastPrice.signum() <= 0) {
            return UNKNOWN_SIMILARITY;
        }
        double past = pastPrice.doubleValue();
        return 1 - Math.min(1, Math.abs(price.doubleValue() - past) / past);
    }

    private double modeSimilarity(ModePreference wanted, LearningMode past) {
        if (wanted == ModePreference.ANY || past == null) {
            return 1.0;
        }
        return wanted.name().equals(past.name()) ? 1.0 : 0.0;
    }
}
