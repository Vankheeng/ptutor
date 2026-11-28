package com.ptutor.backend.entity.enums;

/** How a learned weight model is used (design 12, section 12.7). */
public enum WeightLearningMode {
    /** Not learned; the prior is served. */
    OFF,
    /** Learned every night, but the prior is still served. */
    SHADOW,
    /** The active learned version is served. */
    ACTIVE
}
