package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.dto.recommendation.WeightModel;
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

    private final RecommendationSettings settings;
    private final WeightProvider weightProvider;

    /**
     * @param features    suitability features of the same pair (the level and schedule parts are reused)
     * @param platformRate acceptance rate of the whole platform, the prior for tutors with little history
     */
    public Willingness calculate(
            QueryContext context, TeachingCandidate candidate, FeatureVector features, double platformRate) {
        double history = history(context, candidate, platformRate);
        ModelWeights weights = weightProvider.weights(WeightModel.B_S2T);
        double[] parts = fitParts(context, candidate, features, platformRate);
        double fit = fit(weights, parts);
        double capacity = settings.isCongestionEnabled()
                ? capacity(candidate.remainingSlots(), candidate.pendingApplications()) : 1.0;
        if (candidate.tutorInvitedStudent()) {
            return new Willingness(1.0, history, fit, capacity, true, asList(parts));
        }
        // Flattened form (design 12, 12.2): w_hist x b_hist + Σ w_i x fit_i, which with the prior weights is
        // 0.5 x b_hist + 0.5 x the mean of the fit parts.
        return new Willingness((weights.get(0) * history + fitShare(weights) * fit) * capacity,
                history, fit, capacity, false, asList(parts));
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
            double similarity = settings.getWillingnessKernelGrade()
                            * gradeSimilarity(context.gradeLevel(), decision.gradeLevel())
                    + settings.getWillingnessKernelPrice() * priceSimilarity(price, decision.proposedPrice())
                    + settings.getWillingnessKernelMode()
                            * modeSimilarity(context.learningMode(), decision.learningMode());
            total += similarity;
            if (decision.accepted()) {
                accepted += similarity;
            }
        }
        double alpha = settings.getWillingnessAlpha();
        return (accepted + alpha * platformRate) / (total + alpha);
    }

    /** b_fit: the weighted mean of {@link #fitParts}. */
    double fit(QueryContext context, TeachingCandidate candidate, FeatureVector features, double platformRate) {
        return fit(weightProvider.weights(WeightModel.B_S2T), fitParts(context, candidate, features, platformRate));
    }

    /**
     * Weighted mean of the fit parts; the parts follow the history weight in the model. Without any weight on
     * them (they then do not count in b) it is their plain mean, so the logged value stays meaningful.
     */
    static double fit(ModelWeights weights, double[] parts) {
        double sum = 0;
        double plain = 0;
        for (int i = 0; i < parts.length; i++) {
            sum += weights.get(i + 1) * parts[i];
            plain += parts[i];
        }
        double share = fitShare(weights);
        return share > 0 ? sum / share : plain / parts.length;
    }

    static List<Double> asList(double[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    /** The total weight of the fit parts, everything but the history weight. */
    static double fitShare(ModelWeights weights) {
        double total = 0;
        for (int i = 1; i < weights.model().features().size(); i++) {
            total += weights.get(i);
        }
        return total;
    }

    /**
     * The parts of b_fit in the order of {@link WeightModel#B_S2T} (segment, sched, price). A part we have no
     * data for takes the mean of the parts we have, or the platform rate when we have none (design 12, 12.2):
     * with equal weights the weighted sum is then the mean of the known parts, as b_fit always was.
     */
    double[] fitParts(QueryContext context, TeachingCandidate candidate, FeatureVector features, double platformRate) {
        Double[] parts = { features.segment(), features.sched(),
                priceFromTutorView(context.maxPrice(), candidate.expectedPrice()) };
        double known = 0;
        int count = 0;
        for (Double part : parts) {
            if (part != null) {
                known += part;
                count++;
            }
        }
        double missing = count == 0 ? platformRate : known / count;
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = parts[i] == null ? missing : parts[i];
        }
        return values;
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
        return hi >= p ? 1.0 : Math.max(0, 1 - (p - hi) / (settings.getTutorPriceTolerance() * p));
    }

    private double gradeSimilarity(Integer level, Integer pastLevel) {
        if (level == null || pastLevel == null) {
            return settings.getWillingnessKernelUnknown();
        }
        int distance = Math.abs(level - pastLevel);
        return distance == 0 ? 1.0 : distance == 1 ? settings.getWillingnessKernelGradeAdjacent() : 0.0;
    }

    private double priceSimilarity(BigDecimal price, BigDecimal pastPrice) {
        if (price == null || pastPrice == null || pastPrice.signum() <= 0) {
            return settings.getWillingnessKernelUnknown();
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
