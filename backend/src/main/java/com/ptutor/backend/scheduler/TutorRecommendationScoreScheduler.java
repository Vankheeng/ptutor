package com.ptutor.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.service.TutorRecommendationScoreService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TutorRecommendationScoreScheduler {

    private static final Logger log = LoggerFactory.getLogger(TutorRecommendationScoreScheduler.class);

    private final TutorRepository tutorRepository;
    private final TutorRecommendationScoreService scoreService;
    private final AggregateRefreshJob aggregateRefreshJob;

    @Scheduled(cron = "${app.tutor-recommendation.recalculation-cron:0 30 2 * * *}", zone = "UTC")
    public void recalculateAll() {
        try {
            int refreshed = aggregateRefreshJob.refreshAll();
            log.info("Refreshed aggregate columns of {} tutors", refreshed);
        } catch (Exception exception) {
            // Scores are still recalculated from the previous aggregates.
            log.warn("Unable to refresh tutor aggregate columns", exception);
        }
        tutorRepository.findAllIdsForScoreRecalculation().forEach(tutorId -> {
            try {
                scoreService.recalculateByTutorId(tutorId);
            } catch (Exception exception) {
                log.warn("Unable to recalculate recommendation score for tutor {}", tutorId, exception);
            }
        });
    }
}
