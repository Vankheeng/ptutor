package com.ptutor.backend.entity.enums;

/** How a and b are combined into R ({@code reciprocal.mode}, design 13, 13.5). */
public enum ReciprocalMode {
    /** R = a^α · b^(1−α): α says how much a is trusted over b, and R is 0 when b is 0. */
    GEOMETRIC,
    /** R = 2ab / (a + b), both sides weighed the same (the design until 26/10/2026, kept to compare). */
    HARMONIC
}
