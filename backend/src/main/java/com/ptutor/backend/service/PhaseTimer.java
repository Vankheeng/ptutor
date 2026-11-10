package com.ptutor.backend.service;

import java.util.StringJoiner;

/**
 * Milliseconds spent in each phase of one search, for the debug log ("context=3 vector=180 rank=95 …"). Used to
 * find where the time of a slow search goes (design 09, 9.5, performance).
 */
final class PhaseTimer {

    private final StringJoiner phases = new StringJoiner(" ");
    private long last = System.nanoTime();

    /** Ends the current phase under {@code name} and starts the next one. */
    void mark(String name) {
        long now = System.nanoTime();
        phases.add(name + "=" + (now - last) / 1_000_000);
        last = now;
    }

    @Override
    public String toString() {
        return phases.toString();
    }
}
