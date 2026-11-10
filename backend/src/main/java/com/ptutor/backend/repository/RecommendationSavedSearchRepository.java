package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationSavedSearch;

public interface RecommendationSavedSearchRepository extends JpaRepository<RecommendationSavedSearch, UUID> {

    long countByUser_Id(UUID userId);

    java.util.List<RecommendationSavedSearch> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    java.util.Optional<RecommendationSavedSearch> findByIdAndUser_Id(UUID id, UUID userId);
}
