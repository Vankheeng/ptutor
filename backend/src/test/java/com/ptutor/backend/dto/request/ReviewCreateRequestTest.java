package com.ptutor.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ReviewCreateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5 })
    void acceptsRatingFromOneToFive(int rating) {
        assertThat(validator.validate(new ReviewCreateRequest(rating, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 6, -1 })
    void rejectsRatingOutsideOneToFive(int rating) {
        assertThat(validator.validate(new ReviewCreateRequest(rating, "ok"))).hasSize(1);
    }

    @Test
    void rejectsMissingRating() {
        assertThat(validator.validate(new ReviewCreateRequest(null, "ok"))).hasSize(1);
    }

    @Test
    void rejectsCommentLongerThan2000Characters() {
        assertThat(validator.validate(new ReviewCreateRequest(5, "a".repeat(2001)))).hasSize(1);
        assertThat(validator.validate(new ReviewCreateRequest(5, "a".repeat(2000)))).isEmpty();
    }
}
