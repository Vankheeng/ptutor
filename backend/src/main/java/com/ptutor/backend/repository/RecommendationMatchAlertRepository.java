package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationMatchAlert;

public interface RecommendationMatchAlertRepository extends JpaRepository<RecommendationMatchAlert, UUID> {

    /** A person is told about a posting only once per source of theirs. */
    boolean existsBySourceTypeAndSourceIdAndCandidateTypeAndCandidateId(
            com.ptutor.backend.entity.enums.RecommendationMatchSourceType sourceType, UUID sourceId,
            com.ptutor.backend.entity.enums.RecommendationCandidateType candidateType, UUID candidateId);

    boolean existsByRecipientUser_IdAndCreatedAtAfter(UUID recipientUserId, java.time.LocalDateTime after);
}
