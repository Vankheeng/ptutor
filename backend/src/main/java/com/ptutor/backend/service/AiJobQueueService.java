package com.ptutor.backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.AiJobType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.event.EmbeddingRefreshRequestedEvent;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes rows of the {@code ai_enrichment_jobs} outbox. Always joins the caller's transaction,
 * so a job exists exactly when the change that caused it is committed.
 */
@Service
@RequiredArgsConstructor
public class AiJobQueueService {

    private final AiEnrichmentJobRepository jobRepository;

    /** Plain (not transactional-phase) listener: runs inside the publisher's transaction. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(EmbeddingRefreshRequestedEvent event) {
        // Both at once: the vector does not wait for the LLM, and is refreshed again when tags arrive.
        jobRepository.enqueue(
                AiJobType.ENRICH.name(), event.entityType().name(), event.entityId(), LocalDateTime.now());
        enqueueEmbedding(event.entityType(), event.entityId());
    }

    /**
     * Queues the embedding of one entity. A profile change also re-embeds the open postings of
     * that person, because the posting text contains the profile.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueEmbedding(RecommendationEntityType entityType, UUID entityId) {
        LocalDateTime now = LocalDateTime.now();
        jobRepository.enqueue(AiJobType.EMBED.name(), entityType.name(), entityId, now);
        if (entityType == RecommendationEntityType.TUTOR) {
            jobRepository.enqueueTeachingRequestsOfTutor(entityId, now);
        } else if (entityType == RecommendationEntityType.STUDENT) {
            jobRepository.enqueueStudyingRequestsOfStudent(entityId, now);
        }
    }
}
