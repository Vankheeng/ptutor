package com.ptutor.backend.dto.recommendation;

/**
 * Suitability features of one candidate from the searcher's point of view. Each feature is in
 * [0, 1], or null when the data needed to compute it is missing on either side.
 */
public record FeatureVector(
        Double sem,
        Double need,
        Double style,
        Double segment,
        Double price,
        Double sched,
        Double area,
        /** Weighted mean of the available features, pulled towards neutral by the missing ones. */
        double suitability,
        /** Sum of the weights of the available features: how much of the picture we actually saw. */
        double confidence,
        /** True when both sides gave a schedule and the two never overlap. */
        boolean noScheduleOverlap) {
}
