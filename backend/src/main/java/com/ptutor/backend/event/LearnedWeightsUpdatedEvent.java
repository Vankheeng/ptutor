package com.ptutor.backend.event;

/** A learned weight version was activated or deactivated: the served weights must be loaded again. */
public record LearnedWeightsUpdatedEvent() {
}
