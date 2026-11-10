package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;

public interface RecommendationTagRepository extends JpaRepository<RecommendationTag, UUID> {

    Optional<RecommendationTag> findFirstByTagGroupAndCode(RecommendationTagGroup tagGroup, String code);

    /** Codes are unique over the whole vocabulary, not only within a subject. */
    boolean existsByCode(String code);

    Optional<RecommendationTag> findFirstByCode(String code);

    boolean existsByParent_Id(UUID parentId);

    @EntityGraph(attributePaths = { "subject", "parent" })
    @Query("""
            select tag from RecommendationTag tag
            where (:tagGroup is null or tag.tagGroup = :tagGroup)
              and (:subjectId is null or tag.subject.id = :subjectId)
              and (:status is null or tag.status = :status)
            order by tag.tagGroup, tag.code
            """)
    List<RecommendationTag> search(
            @Param("tagGroup") RecommendationTagGroup tagGroup,
            @Param("subjectId") UUID subjectId,
            @Param("status") RecommendationTagStatus status);

    @EntityGraph(attributePaths = { "subject" })
    List<RecommendationTag> findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus status);

    List<RecommendationTag> findAllByTagGroupAndCodeInAndStatus(
            RecommendationTagGroup tagGroup, Collection<String> codes, RecommendationTagStatus status);
}
