package com.ptutor.backend.scheduler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Refreshes the aggregate columns of {@code tutors} (rating, reviews, completed contracts,
 * students taught, acceptance rate, response time) from the source tables. They are the
 * inputs of the recommendation score, so this always runs before a score recalculation.
 */
@Component
@RequiredArgsConstructor
public class AggregateRefreshJob {

    private final TutorRepository tutorRepository;

    /** Nightly: every tutor in one statement. Returns the number of tutors updated. */
    @Transactional
    public int refreshAll() {
        return tutorRepository.refreshAllAggregates();
    }

    /** After an event that concerns a single tutor. */
    @Transactional
    public void refreshTutor(UUID tutorId) {
        tutorRepository.refreshAggregates(tutorId);
    }
}
