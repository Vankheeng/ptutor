package com.ptutor.backend.entity.enums;

/** How b_cap discounts a posting with applications waiting ({@code willingness.congestion-formula}, design 13, 13.4). */
public enum CongestionFormula {
    /** Only once the applications waiting reach the places left: 1 while n < r, else r / (r + λ (n − r + 1)). */
    ON_SHORTAGE,
    /** From the first application on: r / (r + λ n) (the design until 26/10/2026, kept to compare). */
    EVERY_APPLICATION
}
