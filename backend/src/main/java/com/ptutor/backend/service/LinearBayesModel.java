package com.ptutor.backend.service;

import java.util.Arrays;
import java.util.Random;

/**
 * Bayesian linear regression kept as sufficient statistics (design 12, section 12.3). Pure computation, no
 * Spring: the nightly job updates it, the weight provider samples from it.
 *
 * <pre>
 * r ≈ θ₀ + θ · x + noise            prior θ ~ N(θ_prior, (1/κ) I), θ_prior = (0, w_prior)
 * A₀ = κ I     c₀ = κ θ_prior
 * A  = A₀ + Σ s_i x_i x_iᵀ          c = c₀ + Σ s_i r_i x_i        (x with the intercept x₀ = 1)
 * μ  = A⁻¹ c                         Σ = ν² A⁻¹
 * </pre>
 *
 * Vectors with the intercept have one more element than the features; {@link #project} drops it. Matrices are
 * at most 8 × 8, so the Cholesky decomposition is written by hand.
 */
public final class LinearBayesModel {

    private final double kappa;
    private final double[] priorTheta;
    private final double[][] a;
    private final double[] c;

    private LinearBayesModel(double kappa, double[] priorTheta, double[][] a, double[] c) {
        this.kappa = kappa;
        this.priorTheta = priorTheta;
        this.a = a;
        this.c = c;
    }

    /** No observation yet: μ is the prior, with an intercept of 0. */
    public static LinearBayesModel fromPrior(double[] priorWeights, double kappa) {
        double[] theta = withIntercept(priorWeights);
        int size = theta.length;
        double[][] a = new double[size][size];
        double[] c = new double[size];
        for (int i = 0; i < size; i++) {
            a[i][i] = kappa;
            c[i] = kappa * theta[i];
        }
        return new LinearBayesModel(kappa, theta, a, c);
    }

    /** Continues from stored statistics. */
    public static LinearBayesModel fromStatistics(double[] priorWeights, double kappa, double[][] a, double[] c) {
        double[] theta = withIntercept(priorWeights);
        if (a.length != theta.length || c.length != theta.length) {
            throw new IllegalArgumentException("Statistics of size " + a.length + " for " + theta.length + " weights");
        }
        double[][] copy = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != theta.length) {
                throw new IllegalArgumentException("Matrix A is not square");
            }
            copy[i] = a[i].clone();
        }
        return new LinearBayesModel(kappa, theta, copy, c.clone());
    }

    /** {@code A ← A₀ + γ (A − A₀)}, {@code c ← c₀ + γ (c − c₀)}: old observations count γ times less. */
    public void forget(double gamma) {
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a.length; j++) {
                double prior = i == j ? kappa : 0;
                a[i][j] = prior + gamma * (a[i][j] - prior);
            }
            double prior = kappa * priorTheta[i];
            c[i] = prior + gamma * (c[i] - prior);
        }
    }

    /**
     * Adds one observation.
     *
     * @param features the features without the intercept
     * @param reward   the observed feedback r
     * @param weight   the sample weight s
     */
    public void update(double[] features, double reward, double weight) {
        double[] x = withInterceptOne(features);
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                a[i][j] += weight * x[i] * x[j];
            }
            c[i] += weight * reward * x[i];
        }
    }

    /** μ = A⁻¹ c, with the intercept first. */
    public double[] mean() {
        double[][] l = cholesky(a);
        return backward(l, forward(l, c));
    }

    /**
     * A Thompson sample θ̃ ~ N(μ, scale² ν² A⁻¹), with the intercept first. If A = L Lᵀ and z ~ N(0, I), then
     * L⁻ᵀ z has covariance A⁻¹.
     */
    public double[] sample(Random random, double scale, double noise) {
        double[][] l = cholesky(a);
        double[] mean = backward(l, forward(l, c));
        double[] z = new double[mean.length];
        for (int i = 0; i < z.length; i++) {
            z[i] = random.nextGaussian();
        }
        double[] deviation = backward(l, z);
        for (int i = 0; i < mean.length; i++) {
            mean[i] += scale * noise * deviation[i];
        }
        return mean;
    }

    public double[][] statsA() {
        double[][] copy = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            copy[i] = a[i].clone();
        }
        return copy;
    }

    public double[] statsC() {
        return c.clone();
    }

    /**
     * From θ to weights the ranking can use (design 12, 12.3). θ is first put on the scale of the weights: the
     * intercept is dropped, a negative coefficient counts as 0 and the rest is normalised to a sum of 1. The
     * result is then the point closest to it whose weights sum to 1, each within
     * [max(0.01, min-ratio × prior), max-ratio × prior] and at most {@code maxStep} away from the previous weight.
     * Both limits hold on the final weights; until 21/10/2026 they were applied to the raw θ and then broken by
     * the normalisation (evaluation, design 09, 9.10).
     *
     * @param theta    with the intercept first
     * @param previous the weights served until now (the prior when nothing was learned yet)
     */
    public static double[] project(double[] theta, double[] prior, double[] previous, double minRatio,
            double maxRatio, double maxStep) {
        if (theta.length != prior.length + 1 || previous.length != prior.length) {
            throw new IllegalArgumentException("θ needs one more element than the prior and the previous weights");
        }
        int size = prior.length;
        double[] target = new double[size];
        double total = 0;
        for (int k = 0; k < size; k++) {
            target[k] = Math.max(0, theta[k + 1]);
            total += target[k];
        }
        for (int k = 0; k < size; k++) {
            target[k] = total > 0 ? target[k] / total : previous[k];
        }

        double[] low = new double[size];
        double[] high = new double[size];
        double[] lower = new double[size];
        double[] upper = new double[size];
        for (int k = 0; k < size; k++) {
            low[k] = Math.max(0.01, minRatio * prior[k]);
            high[k] = Math.max(low[k], maxRatio * prior[k]);
            lower[k] = Math.max(low[k], previous[k] - maxStep);
            upper[k] = Math.min(high[k], previous[k] + maxStep);
            if (lower[k] > upper[k]) {
                // The previous weight lies outside the band (the band changed): head for the band.
                lower[k] = upper[k] = Math.min(high[k], Math.max(low[k], previous[k]));
            }
        }
        if (Arrays.stream(lower).sum() > 1 || Arrays.stream(upper).sum() < 1) {
            // The step limit alone cannot reach a sum of 1: keep only the band around the prior.
            lower = low;
            upper = high;
        }
        if (Arrays.stream(lower).sum() > 1 || Arrays.stream(upper).sum() < 1) {
            // Not even the band can (a prior far from a sum of 1): clip and normalise.
            double[] clipped = new double[size];
            for (int k = 0; k < size; k++) {
                clipped[k] = Math.min(high[k], Math.max(low[k], target[k]));
            }
            double sum = Arrays.stream(clipped).sum();
            return Arrays.stream(clipped).map(value -> value / sum).toArray();
        }
        return closestWithSumOne(target, lower, upper);
    }

    /**
     * The point of the box [lower, upper] with a sum of 1 closest to {@code target}: clip(target + t) for the shift
     * t that makes the sum 1, found by bisection (the sum grows with t).
     */
    private static double[] closestWithSumOne(double[] target, double[] lower, double[] upper) {
        double from = Double.POSITIVE_INFINITY;
        double to = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < target.length; k++) {
            from = Math.min(from, lower[k] - target[k]);
            to = Math.max(to, upper[k] - target[k]);
        }
        for (int i = 0; i < 100; i++) {
            double middle = (from + to) / 2;
            if (shifted(target, lower, upper, middle, null) < 1) {
                from = middle;
            } else {
                to = middle;
            }
        }
        double[] weights = new double[target.length];
        shifted(target, lower, upper, (from + to) / 2, weights);
        return weights;
    }

    private static double shifted(double[] target, double[] lower, double[] upper, double shift, double[] out) {
        double sum = 0;
        for (int k = 0; k < target.length; k++) {
            double value = Math.min(upper[k], Math.max(lower[k], target[k] + shift));
            if (out != null) {
                out[k] = value;
            }
            sum += value;
        }
        return sum;
    }

    /** Lower triangular L with A = L Lᵀ; A must be symmetric positive definite. */
    static double[][] cholesky(double[][] matrix) {
        int size = matrix.length;
        double[][] l = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = matrix[i][j];
                for (int k = 0; k < j; k++) {
                    sum -= l[i][k] * l[j][k];
                }
                if (i == j) {
                    if (sum <= 0) {
                        throw new IllegalStateException("Matrix is not positive definite");
                    }
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        return l;
    }

    /** Solves L y = b. */
    private static double[] forward(double[][] l, double[] b) {
        double[] y = new double[b.length];
        for (int i = 0; i < b.length; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) {
                sum -= l[i][k] * y[k];
            }
            y[i] = sum / l[i][i];
        }
        return y;
    }

    /** Solves Lᵀ x = y. */
    private static double[] backward(double[][] l, double[] y) {
        double[] x = new double[y.length];
        for (int i = y.length - 1; i >= 0; i--) {
            double sum = y[i];
            for (int k = i + 1; k < y.length; k++) {
                sum -= l[k][i] * x[k];
            }
            x[i] = sum / l[i][i];
        }
        return x;
    }

    /** (0, w): the prior of the intercept is 0. */
    private static double[] withIntercept(double[] weights) {
        double[] theta = new double[weights.length + 1];
        System.arraycopy(weights, 0, theta, 1, weights.length);
        return theta;
    }

    /** (1, x): the intercept feature is always 1. */
    private static double[] withInterceptOne(double[] features) {
        double[] x = new double[features.length + 1];
        x[0] = 1;
        System.arraycopy(features, 0, x, 1, features.length);
        return x;
    }

    @Override
    public String toString() {
        return "LinearBayesModel[κ=" + kappa + ", μ=" + Arrays.toString(mean()) + "]";
    }
}
