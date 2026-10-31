package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.entity.enums.LessonOutcome;

class RefundCalculatorTest {

    private static final RefundCalculator.Rates RATES = new RefundCalculator.Rates(100, 80);
    private static final BigDecimal VALUE = new BigDecimal("4000000");
    private static final BigDecimal PAID = new BigDecimal("2000000");

    @Test
    void refundsFullLessonPriceWhenTutorDidNotTeach() {
        // 2 × 2,000,000 for 16 lessons: 250,000 per lesson.
        var result = RefundCalculator.calculate(VALUE, 16, PAID, BigDecimal.ZERO,
                List.of(LessonOutcome.TUTOR_ABSENT), RATES);

        assertThat(result.unitPrice()).isEqualByComparingTo("250000");
        assertThat(result.refundAmount()).isEqualByComparingTo("250000");
        assertThat(result.tutorShareAmount()).isEqualByComparingTo("0");
    }

    @Test
    void splitsLessonPriceWhenStudentSkippedIt() {
        var result = RefundCalculator.calculate(VALUE, 16, PAID, BigDecimal.ZERO,
                List.of(LessonOutcome.STUDENT_ABSENT), RATES);

        assertThat(result.refundAmount()).isEqualByComparingTo("200000");
        assertThat(result.tutorShareAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void taughtLessonsRefundNothing() {
        var result = RefundCalculator.calculate(VALUE, 16, PAID, BigDecimal.ZERO,
                List.of(LessonOutcome.TAUGHT, LessonOutcome.TAUGHT), RATES);

        assertThat(result.refundAmount()).isEqualByComparingTo("0");
        assertThat(result.tutorShareAmount()).isEqualByComparingTo("0");
    }

    @Test
    void addsUpMixedOutcomes() {
        var result = RefundCalculator.calculate(VALUE, 16, PAID, BigDecimal.ZERO,
                List.of(LessonOutcome.TUTOR_ABSENT, LessonOutcome.STUDENT_ABSENT, LessonOutcome.TAUGHT), RATES);

        assertThat(result.tutorAbsentLessons()).isEqualTo(1);
        assertThat(result.studentAbsentLessons()).isEqualTo(1);
        assertThat(result.refundAmount()).isEqualByComparingTo("450000");
        assertThat(result.tutorShareAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void neverRefundsMoreThanPaidMinusEarlierRefunds() {
        var result = RefundCalculator.calculate(VALUE, 16, new BigDecimal("300000"), new BigDecimal("100000"),
                List.of(LessonOutcome.TUTOR_ABSENT, LessonOutcome.TUTOR_ABSENT), RATES);

        assertThat(result.refundableAmount()).isEqualByComparingTo("200000");
        assertThat(result.refundAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void roundsLessonPriceToWholeDongAndKeepsPartsAddingUp() {
        // 1,000,000 for 3 lessons: 333,333 per lesson; 80% is 266,666 and the tutor keeps 66,667.
        var result = RefundCalculator.calculate(new BigDecimal("1000000"), 3, new BigDecimal("1000000"),
                BigDecimal.ZERO, List.of(LessonOutcome.STUDENT_ABSENT), RATES);

        assertThat(result.unitPrice()).isEqualByComparingTo("333333");
        assertThat(result.refundAmount()).isEqualByComparingTo("266666");
        assertThat(result.tutorShareAmount()).isEqualByComparingTo("66667");
    }
}
