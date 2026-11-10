package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.entity.RecommendationDecisionSnapshot;
import com.ptutor.backend.entity.enums.DecisionRequestType;

@Repository
public interface RecommendationDecisionSnapshotRepository extends JpaRepository<RecommendationDecisionSnapshot, Long> {

    List<RecommendationDecisionSnapshot> findAllByRequestTypeAndRequestIdOrderByModelKey(
            DecisionRequestType requestType, UUID requestId);
}
