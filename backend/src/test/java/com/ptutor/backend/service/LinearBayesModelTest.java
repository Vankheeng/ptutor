package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class LinearBayesModelTest {

    private static final double[] PRIOR = { 0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05 };

    @Test
    void withoutDataTheMeanIsThePriorAndTheInterceptIsZero() {
        double[] mean = LinearBayesModel.fromPrior(PRIOR, 200, 200, 1).mean();

        assertThat(mean[0]).isCloseTo(0, within(1e-12));
        assertThat(Arrays.copyOfRange(mean, 1, mean.length)).containsExactly(PRIOR, within(1e-12));
    }

    @Test
    void withoutDataTheMeanIsTheScaledPriorAndTheInterceptIsZero() {
        double[] mean = LinearBayesModel.fromPrior(PRIOR, 200, 1, 0.3).mean();

        assertThat(mean[0]).isCloseTo(0, within(1e-12));
        assertThat(Arrays.copyOfRange(mean, 1, mean.length))
                .containsExactly(Arrays.stream(PRIOR).map(w -> 0.3 * w).toArray(), within(1e-12));
    }

    @Test
    void theScaleOfThePriorIsTheSlopeOfTheRewardOnThePriorScore() {
        Random random = new Random(3);
        double[][] x = new double[5_000][];
        double[] r = new double[x.length];
        double[] s = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.doubles(PRIOR.length).toArray();
            double score = 0;
            for (int k = 0; k < PRIOR.length; k++) {
                score += PRIOR[k] * x[i][k];
            }
            r[i] = 0.1 + 0.3 * score + 0.05 * random.nextGaussian();
            s[i] = 1;
        }

        assertThat(LinearBayesModel.priorScale(PRIOR, x, r, s)).isCloseTo(0.3, within(0.02));
        assertThat(LinearBayesModel.priorScale(PRIOR, x, Arrays.stream(r).map(v -> -v).toArray(), s))
                .as("never below 0.01").isEqualTo(0.01);
    }

    @Test
    void aWeakInterceptPriorLearnsTheBaseRateWithoutBendingTheWeights() {
        double[] truth = { 0.15, 0.20, 0.35, 0.10, 0.10, 0.05, 0.05 };
        double base = 0.1;
        Random random = new Random(5);
        double[][] x = new double[20_000][];
        double[] r = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.doubles(truth.length).toArray();
            double score = 0;
            for (int k = 0; k < truth.length; k++) {
                score += truth[k] * x[i][k];
            }
            r[i] = base + 0.3 * score + 0.05 * random.nextGaussian();
        }
        double beta = LinearBayesModel.priorScale(PRIOR, Arrays.copyOf(x, 1_000), Arrays.copyOf(r, 1_000),
                Arrays.stream(new double[1_000]).map(v -> 1).toArray());
        LinearBayesModel fixed = LinearBayesModel.fromPrior(PRIOR, 200, 1, beta);
        LinearBayesModel before = LinearBayesModel.fromPrior(PRIOR, 200, 200, 1);
        for (int i = 0; i < x.length; i++) {
            fixed.update(x[i], r[i], 1);
            before.update(x[i], r[i], 1);
        }

        double[] mean = fixed.mean();
        double[] weights = LinearBayesModel.project(mean, PRIOR, PRIOR, 0.01, 100, 1);
        double[] oldWeights = LinearBayesModel.project(before.mean(), PRIOR, PRIOR, 0.01, 100, 1);
        assertThat(mean[0]).isCloseTo(base, within(0.02));
        assertThat(weights).containsExactly(truth, within(0.03));
        assertThat(distance(weights, truth)).as("closer to the truth than with κ₀ = κ and β = 1")
                .isLessThan(distance(oldWeights, truth));
    }

    private static double distance(double[] left, double[] right) {
        double sum = 0;
        for (int k = 0; k < left.length; k++) {
            sum += Math.abs(left[k] - right[k]);
        }
        return sum;
    }

    @Test
    void theMeanMovesToTheWeightsThatProducedTheData() {
        double[] truth = { 0.10, 0.40, 0.10, 0.05, 0.25, 0.05, 0.05 };
        double intercept = 0.08;
        Random random = new Random(7);
        LinearBayesModel model = LinearBayesModel.fromPrior(PRIOR, 5, 5, 1);

        for (int i = 0; i < 20_000; i++) {
            double[] x = new double[truth.length];
            double r = intercept;
            for (int k = 0; k < x.length; k++) {
                x[k] = random.nextDouble();
                r += truth[k] * x[k];
            }
            model.update(x, r + 0.05 * random.nextGaussian(), 1);
        }

        double[] mean = model.mean();
        assertThat(mean[0]).isCloseTo(intercept, within(0.01));
        assertThat(Arrays.copyOfRange(mean, 1, mean.length)).containsExactly(truth, within(0.01));
    }

    @Test
    void theStrongerThePriorTheLessAFewObservationsMoveTheMean() {
        double[] x = { 1, 0, 0, 0, 0, 0, 0 };
        LinearBayesModel weak = LinearBayesModel.fromPrior(PRIOR, 1, 1, 1);
        LinearBayesModel strong = LinearBayesModel.fromPrior(PRIOR, 1_000, 1_000, 1);
        for (int i = 0; i < 50; i++) {
            weak.update(x, 1.0, 1);
            strong.update(x, 1.0, 1);
        }

        double weakShift = Math.abs(weak.mean()[1] - PRIOR[0]);
        double strongShift = Math.abs(strong.mean()[1] - PRIOR[0]);
        assertThat(strongShift).isLessThan(weakShift / 5);
    }

    @Test
    void aSampleWeightCountsLikeRepeatingTheObservation() {
        double[] x = { 0.5, 1, 0, 0.2, 0, 1, 0.3 };
        LinearBayesModel weighted = LinearBayesModel.fromPrior(PRIOR, 10, 10, 1);
        LinearBayesModel repeated = LinearBayesModel.fromPrior(PRIOR, 10, 10, 1);

        weighted.update(x, 0.8, 3);
        for (int i = 0; i < 3; i++) {
            repeated.update(x, 0.8, 1);
        }

        assertThat(weighted.mean()).containsExactly(repeated.mean(), within(1e-12));
    }

    @Test
    void forgettingWithFactorOneChangesNothingAndWithZeroReturnsToThePrior() {
        LinearBayesModel model = LinearBayesModel.fromPrior(PRIOR, 10, 10, 1);
        model.update(new double[] { 1, 1, 0, 0, 0, 0, 0 }, 1, 5);
        double[] learned = model.mean();

        model.forget(1.0);
        assertThat(model.mean()).containsExactly(learned, within(1e-12));

        model.forget(0.0);
        assertThat(model.mean()).containsExactly(LinearBayesModel.fromPrior(PRIOR, 10, 10, 1).mean(), within(1e-12));
    }

    @Test
    void statisticsRoundTripToTheSameModel() {
        LinearBayesModel model = LinearBayesModel.fromPrior(PRIOR, 10, 10, 1);
        model.update(new double[] { 0.2, 0.4, 0.6, 0.8, 1, 0, 0.5 }, 0.6, 2);

        LinearBayesModel restored = LinearBayesModel.fromStatistics(PRIOR, 10, 10, 1, model.statsA(), model.statsC());

        assertThat(restored.mean()).containsExactly(model.mean(), within(1e-12));
        assertThatThrownBy(() -> LinearBayesModel.fromStatistics(PRIOR, 10, 10, 1, new double[2][2], new double[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choleskyReconstructsTheMatrix() {
        double[][] matrix = { { 4, 2, 0.6 }, { 2, 5, 1 }, { 0.6, 1, 3 } };

        double[][] l = LinearBayesModel.cholesky(matrix);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += l[i][k] * l[j][k];
                }
                assertThat(sum).isCloseTo(matrix[i][j], within(1e-12));
            }
        }
        assertThatThrownBy(() -> LinearBayesModel.cholesky(new double[][] { { 1, 2 }, { 2, 1 } }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSameSeedGivesTheSameSampleAndSamplesSpreadAroundTheMean() {
        LinearBayesModel model = LinearBayesModel.fromPrior(PRIOR, 50, 50, 1);

        assertThat(model.sample(new Random(42), 1.0, 0.3)).containsExactly(model.sample(new Random(42), 1.0, 0.3));
        assertThat(model.sample(new Random(42), 1.0, 0.3)).isNotEqualTo(model.sample(new Random(43), 1.0, 0.3));
        assertThat(model.sample(new Random(42), 0.0, 0.3)).containsExactly(model.mean(), within(1e-12));

        // Σ = ν² A⁻¹ = 0.09 / 50 on each axis: the sample mean and variance of the first weight.
        Random random = new Random(1);
        int count = 20_000;
        double sum = 0;
        double squares = 0;
        for (int i = 0; i < count; i++) {
            double value = model.sample(random, 1.0, 0.3)[1];
            sum += value;
            squares += value * value;
        }
        double mean = sum / count;
        assertThat(mean).isCloseTo(PRIOR[0], within(0.002));
        assertThat(squares / count - mean * mean).isCloseTo(0.09 / 50, within(0.0002));
    }

    @Test
    void projectionClampsAroundThePriorLimitsTheStepAndSumsToOne() {
        double[] prior = { 0.20, 0.20, 0.60 };
        // Intercept, then far above 2 × prior, negative, and within range.
        double[] theta = { 0.7, 0.55, -0.3, 0.60 };

        double[] unlimited = LinearBayesModel.project(theta, prior, prior, 0.5, 2.0, 1.0);
        // On the scale of the weights θ is (0.55, 0, 0.60) / 1.15; the closest weights summing to 1 within
        // [0.1, 0.4] x [0.1, 0.4] x [0.3, 1.2] are 0.40, 0.10, 0.50: the band holds after normalising.
        assertThat(unlimited).containsExactly(new double[] { 0.40, 0.10, 0.50 }, within(1e-9));

        double[] limited = LinearBayesModel.project(theta, prior, prior, 0.5, 2.0, 0.05);
        // At most 0.05 away from the previous weights, still after normalising.
        assertThat(limited).containsExactly(new double[] { 0.25, 0.15, 0.60 }, within(1e-9));
        assertThat(Arrays.stream(limited).sum()).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void bandAndStepHoldOnTheFinalWeightsWhateverTheScaleOfTheta() {
        double[] prior = PRIOR;
        Random random = new Random(11);
        for (int round = 0; round < 500; round++) {
            double[] theta = new double[prior.length + 1];
            double scale = 0.1 + 10 * random.nextDouble();
            for (int k = 0; k < theta.length; k++) {
                theta[k] = scale * (random.nextDouble() - 0.2);
            }
            double[] previous = LinearBayesModel.project(theta, prior, prior, 0.5, 2.0, 1.0);

            double[] weights = LinearBayesModel.project(
                    new double[] { 0, 0.05, 0.6, 0.05, 0.1, 0.1, 0.05, 0.05 }, prior, previous, 0.5, 2.0, 0.05);

            assertThat(Arrays.stream(weights).sum()).isCloseTo(1.0, within(1e-9));
            for (int k = 0; k < prior.length; k++) {
                assertThat(weights[k]).isBetween(Math.max(0.01, 0.5 * prior[k]) - 1e-9, 2 * prior[k] + 1e-9);
                assertThat(Math.abs(weights[k] - previous[k])).isLessThanOrEqualTo(0.05 + 1e-9);
            }
        }
    }

    @Test
    void projectionNeverSwitchesAFeatureOff() {
        double[] prior = { 0.01, 0.99 };

        double[] weights = LinearBayesModel.project(new double[] { 0, -5, 0.99 }, prior, prior, 0.5, 2.0, 1.0);

        assertThat(weights[0]).isGreaterThan(0);
    }
}
