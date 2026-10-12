package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationEntityTag;
import com.ptutor.backend.entity.enums.RecommendationEntityType;

public interface RecommendationEntityTagRepository extends JpaRepository<RecommendationEntityTag, UUID> {

    @EntityGraph(attributePaths = { "tag", "tag.subject" })
    List<RecommendationEntityTag> findAllByEntityTypeAndEntityId(RecommendationEntityType entityType, UUID entityId);
}
