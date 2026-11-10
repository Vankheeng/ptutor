package com.ptutor.backend.dto.recommendation;

import java.util.List;

/**
 * The four sets of ranking weights that L1 learns (design 12, section 12.2), with the names of their features
 * in a fixed order.
 */
public enum WeightModel {

    /** a: suitability of a teaching posting for the student. */
    A_S2T(List.of("sem", "need", "price", "style", "sched", "area", "segment")),
    /** a': suitability of a studying posting for the tutor. */
    A_T2S(List.of("sem", "price", "need", "segment", "sched", "area")),
    /** b before b_cap, flattened: the tutor's willingness to take the student. */
    B_S2T(List.of("hist", "fitSegment", "fitSched", "fitPrice")),
    /** b' before b'_cap, flattened: the student's willingness to accept the tutor. */
    B_T2S(List.of("hist", "fitQuality", "fitStyle", "fitPrice"));

    private final List<String> features;

    WeightModel(List<String> features) {
        this.features = features;
    }

    public List<String> features() {
        return features;
    }
}
