package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.RecommendationMatchAlert;

public interface RecommendationMatchAlertRepository extends JpaRepository<RecommendationMatchAlert, UUID> {
}
