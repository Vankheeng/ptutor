package com.ptutor.backend.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationClickLog;

public interface RecommendationClickLogRepository extends JpaRepository<RecommendationClickLog, UUID> {

    java.util.List<RecommendationClickLog> findAllByRecommendationLog_IdOrderByCreatedAtAsc(UUID recommendationLogId);

    Optional<RecommendationClickLog>
            findFirstByRecommendationLog_SearcherStudent_User_IdAndClickedTutor_IdOrderByCreatedAtDesc(
                    UUID studentUserId, UUID tutorId);

    Optional<RecommendationClickLog>
            findFirstByRecommendationLog_SearcherStudent_User_IdAndCandidateTeachingRequest_IdOrderByCreatedAtDesc(
                    UUID studentUserId, UUID teachingRequestId);
}
