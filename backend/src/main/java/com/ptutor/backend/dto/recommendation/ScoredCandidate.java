package com.ptutor.backend.dto.recommendation;

/**
 * A candidate with its final score. {@code a} is the suitability, {@code q} the tutor quality in
 * [0, 1] and {@code g} the gate; {@code score = a x (qualityBase + qualityWeight x q) x g}.
 */
public record ScoredCandidate(
        TeachingCandidate candidate,
        FeatureVector features,
        double a,
        double q,
        double g,
        double score) {
}
