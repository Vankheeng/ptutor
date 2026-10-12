package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.AiEnrichmentJob;

public interface AiEnrichmentJobRepository extends JpaRepository<AiEnrichmentJob, UUID> {
}
