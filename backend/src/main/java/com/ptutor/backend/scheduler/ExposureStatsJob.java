package com.ptutor.backend.scheduler;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.repository.RecommendationExposureStatsRepository;
import com.ptutor.backend.service.RecommendationSettings;

import lombok.RequiredArgsConstructor;

/**
 * Every night, counts how often each posting was shown on a first page and how often it was opened
 * (design 06, 6.11). The exploration slots read these numbers.
 */
@Component
@RequiredArgsConstructor
public class ExposureStatsJob {

    private static final Logger log = LoggerFactory.getLogger(ExposureStatsJob.class);

    private final RecommendationExposureStatsRepository statsRepository;
    private final RecommendationSettings settings;

    /** 20:00 UTC is 03:00 in Vietnam. */
    @Scheduled(cron = "${app.recommendation.exploration.stats-cron:0 0 20 * * *}", zone = "UTC")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        statsRepository.clearWeek(now);
        int postings = statsRepository.recount(now.minusDays(7), settings.getExplorationPageSize(), now);
        log.info("Exposure statistics recounted for {} postings", postings);
    }
}
