package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.entity.enums.DecisionSampleKind;
import com.ptutor.backend.repository.LearningDataRepository.DecisionSample;

/** The held-out comparison of the willingness side (design 12, 12.7; 13, 13.7). */
class WeightLearningServiceAucTest {

    private static final double[] HIST_ONLY = { 1, 0, 0, 0 };

    @Test
    void countsTheAcceptedRequestsScoredAboveTheRefusedOnesWithTheirWeights() {
        List<DecisionSample> samples = List.of(
                sample(0.9, 1, 1.5), sample(0.4, 1, 1), // accepted
                sample(0.5, 0, 1), sample(0.1, 0, 0.5)); // refused

        // Pairs (w): 0.9>0.5 (1.5), 0.9>0.1 (0.75), 0.4<0.5 (1), 0.4>0.1 (0.5) → (1.5 + 0.75 + 0.5) / 3.75.
        assertThat(WeightLearningService.auc(samples, HIST_ONLY)).isCloseTo(2.75 / 3.75, within(1e-12));
    }

    @Test
    void aTieCountsHalfAndOnlyTheOrderMatters() {
        List<DecisionSample> samples = List.of(sample(0.5, 1, 1), sample(0.5, 0, 1), sample(0.2, 0, 1));

        assertThat(WeightLearningService.auc(samples, HIST_ONLY)).isCloseTo(0.75, within(1e-12));
        double[] scaled = { 3, 0, 0, 0 };
        assertThat(WeightLearningService.auc(samples, scaled)).isEqualTo(WeightLearningService.auc(samples, HIST_ONLY));
    }

    @Test
    void withoutBothLabelsThereIsNoPairToCompare() {
        assertThat(WeightLearningService.auc(List.of(sample(0.5, 1, 1)), HIST_ONLY)).isZero();
    }

    private static DecisionSample sample(double hist, double label, double weight) {
        return new DecisionSample(UUID.randomUUID(), DecisionSampleKind.DECISION, new double[] { hist, 0.5, 0.5, 0.5 },
                label, weight, LocalDateTime.of(2026, 10, 20, 9, 0));
    }
}
