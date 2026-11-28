package com.ptutor.backend.dto.recommendation;

/**
 * A studying posting with its final score for a tutor: {@code a} is the suitability for the tutor,
 * {@code willingness} the student's side (null when the reciprocal part is off), {@code r} their
 * harmonic mean and {@code g} the gate; {@code score = r x g} (students have no quality factor).
 */
public record ScoredStudyingCandidate(
        StudyingCandidate candidate,
        FeatureVector features,
        double a,
        Willingness willingness,
        double r,
        double g,
        double score) {
}
