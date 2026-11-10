package com.ptutor.backend.dto.recommendation;

/**
 * A candidate with its final score. {@code a} is the suitability for the searcher, {@code willingness}
 * the other side (null when the reciprocal part is switched off), {@code r} their harmonic mean (equal
 * to {@code a} without it), {@code q} the tutor quality in [0, 1] and {@code g} the gate;
 * {@code score = r x (qualityBase + qualityWeight x q) x g}.
 */
public record ScoredCandidate(
        TeachingCandidate candidate,
        FeatureVector features,
        double a,
        Willingness willingness,
        double r,
        double q,
        double g,
        double score) {
}
