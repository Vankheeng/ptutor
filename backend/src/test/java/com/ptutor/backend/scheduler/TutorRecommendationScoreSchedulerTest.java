package com.ptutor.backend.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.service.TutorRecommendationScoreService;

@ExtendWith(MockitoExtension.class)
class TutorRecommendationScoreSchedulerTest {

    @Mock TutorRepository tutorRepository;
    @Mock TutorRecommendationScoreService scoreService;
    @Mock AggregateRefreshJob aggregateRefreshJob;

    private TutorRecommendationScoreScheduler scheduler;
    private UUID firstTutorId;
    private UUID secondTutorId;

    @BeforeEach
    void setUp() {
        scheduler = new TutorRecommendationScoreScheduler(tutorRepository, scoreService, aggregateRefreshJob);
        firstTutorId = UUID.randomUUID();
        secondTutorId = UUID.randomUUID();
        when(tutorRepository.findAllIdsForScoreRecalculation()).thenReturn(List.of(firstTutorId, secondTutorId));
    }

    @Test
    void refreshesAggregatesBeforeRecalculatingScores() {
        scheduler.recalculateAll();

        InOrder order = inOrder(aggregateRefreshJob, scoreService);
        order.verify(aggregateRefreshJob).refreshAll();
        order.verify(scoreService).recalculateByTutorId(firstTutorId);
        order.verify(scoreService).recalculateByTutorId(secondTutorId);
    }

    @Test
    void stillRecalculatesScoresWhenAggregateRefreshFails() {
        when(aggregateRefreshJob.refreshAll()).thenThrow(new IllegalStateException("boom"));

        scheduler.recalculateAll();

        verify(scoreService).recalculateByTutorId(firstTutorId);
        verify(scoreService).recalculateByTutorId(secondTutorId);
    }

    @Test
    void failureForOneTutorDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("boom")).when(scoreService).recalculateByTutorId(firstTutorId);

        scheduler.recalculateAll();

        verify(scoreService).recalculateByTutorId(secondTutorId);
    }
}
