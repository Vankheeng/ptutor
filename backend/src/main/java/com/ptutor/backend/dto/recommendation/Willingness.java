package com.ptutor.backend.dto.recommendation;

/**
 * How likely the tutor is to accept this student for this posting ("b"), with its parts:
 * {@code b = (0.5 x history + 0.5 x fit) x capacity}, or 1 when the tutor already invited the student.
 */
public record Willingness(double b, double history, double fit, double capacity, boolean invited) {
}
