package com.ptutor.backend.dto.request;

import java.util.UUID;

import com.ptutor.backend.entity.enums.RecommendationAction;

import jakarta.validation.constraints.NotNull;

/**
 * Something the user did with one result of a search.
 *
 * @param rank accepted for compatibility with the design; the rank stored with the search is what is recorded
 */
public record RecommendationEventRequest(
        @NotNull(message = "Action is required") RecommendationAction action,
        Integer rank,
        @NotNull(message = "Candidate is required") UUID candidateId) {
}
