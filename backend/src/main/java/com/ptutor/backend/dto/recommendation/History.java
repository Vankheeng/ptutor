package com.ptutor.backend.dto.recommendation;

import java.util.Map;
import java.util.UUID;

/**
 * What the searcher's long-term preference profile says about the candidates of one search
 * (design 06, 6.4 "f_hist"): how much each candidate resembles what they showed interest in before,
 * and how much weight that gets. The weight grows with the amount of history and is 0 for a new user.
 */
public record History(double weight, Map<UUID, Double> byCandidate) {

    public static final History NONE = new History(0, Map.of());

    /** f_hist of a candidate, or null when there is no profile to compare it with. */
    public Double of(UUID candidateId) {
        return weight <= 0 ? null : byCandidate.get(candidateId);
    }
}
