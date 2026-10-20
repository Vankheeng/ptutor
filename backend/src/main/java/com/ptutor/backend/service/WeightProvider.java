package com.ptutor.backend.service;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.WeightModel;

import lombok.RequiredArgsConstructor;

/**
 * The weights the rankers use (design 12, section 12.9). The calculators ask this class instead of reading
 * {@link RecommendationSettings}, so learned weights can be served later without touching them. For now it
 * only serves the prior: the hand-set weights of the settings, so the ranking is the same as before.
 */
@Component
@RequiredArgsConstructor
public class WeightProvider {

    private final RecommendationSettings settings;

    public ModelWeights weights(WeightModel model) {
        return prior(model);
    }

    /** The hand-set weights, which are also the starting point of learning. */
    public ModelWeights prior(WeightModel model) {
        return switch (model) {
            case A_S2T -> new ModelWeights(model, settings.getWeightSem(), settings.getWeightNeed(),
                    settings.getWeightPrice(), settings.getWeightStyle(), settings.getWeightSched(),
                    settings.getWeightArea(), settings.getWeightSegment());
            case A_T2S -> new ModelWeights(model, settings.getTutorWeightSem(), settings.getTutorWeightPrice(),
                    settings.getTutorWeightNeed(), settings.getTutorWeightSegment(), settings.getTutorWeightSched(),
                    settings.getTutorWeightArea());
            case B_S2T -> flattened(model, settings.getWillingnessHistoryShare(), settings.getWillingnessFitSegment(),
                    settings.getWillingnessFitSched(), settings.getWillingnessFitPrice());
            case B_T2S -> flattened(model, settings.getTutorWillingnessHistoryShare(),
                    settings.getTutorWillingnessFitQuality(), settings.getTutorWillingnessFitStyle(),
                    settings.getTutorWillingnessFitPrice());
        };
    }

    /**
     * {@code share × hist + (1 - share) × weighted mean of the fit parts} written as one weighted sum
     * (design 12, section 12.2): each fit part gets {@code (1 - share) × w_i / Σ w}.
     */
    private static ModelWeights flattened(WeightModel model, double historyShare, double... fitWeights) {
        double total = 0;
        for (double weight : fitWeights) {
            total += weight;
        }
        double[] values = new double[fitWeights.length + 1];
        values[0] = historyShare;
        for (int i = 0; i < fitWeights.length; i++) {
            values[i + 1] = (1 - historyShare) * fitWeights[i] / total;
        }
        return new ModelWeights(model, values);
    }
}
