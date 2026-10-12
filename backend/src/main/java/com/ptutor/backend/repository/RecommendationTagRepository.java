package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;

public interface RecommendationTagRepository extends JpaRepository<RecommendationTag, UUID> {

    List<RecommendationTag> findAllByTagGroupAndCodeInAndStatus(
            RecommendationTagGroup tagGroup, Collection<String> codes, RecommendationTagStatus status);
}
