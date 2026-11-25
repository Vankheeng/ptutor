package com.ptutor.backend.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.service.LessonService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LessonAutoCompletionScheduler {

    private final LessonService lessonService;

    @Value("${app.lesson.auto-completion-grace-days:3}")
    private int graceDays;

    @Scheduled(fixedDelayString = "${app.lesson.auto-completion-interval-ms:3600000}")
    public void completeExpiredPendingLessons() {
        lessonService.completeExpiredPendingLessons(graceDays);
    }
}
