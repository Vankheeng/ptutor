package com.ptutor.backend.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ptutor.backend.entity.enums.LessonOutcome;

/**
 * Share of a disputed lesson's price refunded to the student per outcome, and the tutor's response window,
 * configurable under {@code app.refund}.
 */
@Component
public class RefundPolicy {

    private final int tutorAbsentRate;
    private final int studentAbsentRate;
    private final Duration tutorResponseWindow;

    public RefundPolicy(
            @Value("${app.refund.rates.tutor-absent:100}") int tutorAbsentRate,
            @Value("${app.refund.rates.student-absent:80}") int studentAbsentRate,
            @Value("${app.refund.tutor-response-hours:48}") long tutorResponseHours) {
        this.tutorAbsentRate = tutorAbsentRate;
        this.studentAbsentRate = studentAbsentRate;
        this.tutorResponseWindow = Duration.ofHours(tutorResponseHours);
    }

    public int tutorAbsentRate() {
        return tutorAbsentRate;
    }

    public int studentAbsentRate() {
        return studentAbsentRate;
    }

    public Duration tutorResponseWindow() {
        return tutorResponseWindow;
    }

    public RefundCalculator.Rates rates() {
        return new RefundCalculator.Rates(tutorAbsentRate, studentAbsentRate);
    }

    static boolean isAbsence(LessonOutcome outcome) {
        return outcome == LessonOutcome.TUTOR_ABSENT || outcome == LessonOutcome.STUDENT_ABSENT;
    }
}
