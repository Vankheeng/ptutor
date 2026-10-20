package com.ptutor.backend.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.service.WeightLearningService;

import lombok.RequiredArgsConstructor;

/**
 * Every night, updates the learned ranking weights (design 12, section 12.7). 20:30 UTC is 03:30 in Vietnam, after
 * {@link ExposureStatsJob}.
 */
@Component
@RequiredArgsConstructor
public class WeightLearningJob {

    private final WeightLearningService learningService;
    private final Clock clock;

    @Scheduled(cron = "${app.recommendation.learning.job-cron:0 30 20 * * *}", zone = "UTC")
    public void run() {
        learningService.learnAll(LocalDateTime.now(clock));
    }
}
