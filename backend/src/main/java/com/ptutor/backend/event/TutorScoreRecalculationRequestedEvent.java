package com.ptutor.backend.event;

import java.util.Set;
import java.util.UUID;

/**
 * Asks for the recommendation score of the given tutors to be recalculated after the
 * publishing transaction commits. Not a notification: no user is told about it.
 */
public record TutorScoreRecalculationRequestedEvent(Set<UUID> tutorIds) {

    public TutorScoreRecalculationRequestedEvent {
        tutorIds = tutorIds == null ? Set.of() : Set.copyOf(tutorIds);
    }
}
