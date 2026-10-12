package com.ptutor.backend.event;

import java.util.UUID;

import com.ptutor.backend.entity.enums.RecommendationEntityType;

/**
 * The text of a profile or posting changed, so its embedding must be refreshed. Handled
 * synchronously inside the publishing transaction (outbox): the job row commits or rolls back
 * together with the change.
 */
public record EmbeddingRefreshRequestedEvent(RecommendationEntityType entityType, UUID entityId) {
}
