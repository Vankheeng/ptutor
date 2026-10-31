package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import com.ptutor.backend.entity.enums.LessonOutcome;

/**
 * Refund arithmetic per disputed lesson. A lesson the tutor missed is refunded at the tutor-absent rate; a lesson
 * the student skipped is refunded at the student-absent rate and the rest of its price goes to the tutor.
 */
public final class RefundCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RefundCalculator() {
    }

    public record Rates(int tutorAbsentPercent, int studentAbsentPercent) {
    }

    public record Result(
            BigDecimal unitPrice,
            BigDecimal paidAmount,
            BigDecimal previouslyRefundedAmount,
            BigDecimal refundableAmount,
            int tutorAbsentLessons,
            int studentAbsentLessons,
            BigDecimal refundAmount,
            BigDecimal tutorShareAmount) {
    }

    /**
     * @param contractValue sum of the contract's non-cancelled installments
     * @param outcomes      one outcome per disputed lesson; TAUGHT lessons refund nothing
     */
    public static Result calculate(
            BigDecimal contractValue,
            int totalLessons,
            BigDecimal paidAmount,
            BigDecimal previouslyRefundedAmount,
            Collection<LessonOutcome> outcomes,
            Rates rates) {
        BigDecimal paid = zeroIfNull(paidAmount);
        BigDecimal previous = zeroIfNull(previouslyRefundedAmount);
        // Whole đồng per lesson, so that the student's and the tutor's parts add up to the lesson price.
        BigDecimal unitPrice = totalLessons > 0
                ? zeroIfNull(contractValue).divide(BigDecimal.valueOf(totalLessons), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal tutorAbsentPart = portion(unitPrice, rates.tutorAbsentPercent());
        BigDecimal studentAbsentPart = portion(unitPrice, rates.studentAbsentPercent());

        int tutorAbsent = (int) outcomes.stream().filter(outcome -> outcome == LessonOutcome.TUTOR_ABSENT).count();
        int studentAbsent = (int) outcomes.stream().filter(outcome -> outcome == LessonOutcome.STUDENT_ABSENT).count();
        BigDecimal refundable = paid.subtract(previous).max(BigDecimal.ZERO);
        BigDecimal refund = tutorAbsentPart.multiply(BigDecimal.valueOf(tutorAbsent))
                .add(studentAbsentPart.multiply(BigDecimal.valueOf(studentAbsent)))
                .min(refundable);
        BigDecimal tutorShare = unitPrice.subtract(studentAbsentPart).multiply(BigDecimal.valueOf(studentAbsent));

        return new Result(scaled(unitPrice), scaled(paid), scaled(previous), scaled(refundable),
                tutorAbsent, studentAbsent, scaled(refund), scaled(tutorShare));
    }

    private static BigDecimal portion(BigDecimal unitPrice, int percent) {
        return unitPrice.multiply(BigDecimal.valueOf(percent)).divide(HUNDRED, 0, RoundingMode.FLOOR);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
