package com.ptutor.backend.dto.recommendation;

/**
 * Suitability features of one candidate from the searcher's point of view. Each feature is in
 * [0, 1], or null when the data needed to compute it is missing on either side.
 */
public record FeatureVector(
        Double sem,
        /** Cross-encoder score that went into {@code sem}; null when the posting was not reranked. */
        Double rerank,
        Double need,
        Double style,
        Double segment,
        Double price,
        Double sched,
        Double area,
        /** Resemblance to what the searcher showed interest in before; null without a preference profile. */
        Double hist,
        /** Share of {@code hist} in the suitability: 0 for a new user, up to 0.15 with enough history. */
        double histWeight,
        /**
         * Weighted mean of the available features, pulled towards neutral by the missing ones, then
         * blended with {@code hist}: {@code (1 - histWeight) x base + histWeight x hist}.
         */
        double suitability,
        /** Sum of the weights of the available features: how much of the picture we actually saw. */
        double confidence,
        /** True when both sides gave a schedule and the two never overlap. */
        boolean noScheduleOverlap) {
}
