package com.ptutor.backend.dto.recommendation;

import java.util.List;

/**
 * How likely the other side is to accept ("b", or "b'" for a tutor's proposal), with its parts:
 * {@code b = (0.5 x history + 0.5 x fit) x capacity} with the prior weights, or 1 when the other side reached out
 * first.
 *
 * @param fitParts the parts of {@code fit} as used in b, in the order of the fit features of
 *                 {@link WeightModel#B_S2T} or {@link WeightModel#B_T2S}
 */
public record Willingness(double b, double history, double fit, double capacity, boolean invited,
        List<Double> fitParts) {
}
