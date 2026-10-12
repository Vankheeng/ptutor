package com.ptutor.backend.dto.recommendation;

/** Where a merged search criterion came from, in priority order. */
public enum CriterionSource {
    FILTER,
    STUDYING_REQUEST,
    TEACHING_REQUEST,
    PROFILE,
    DEFAULT
}
