package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Tutor;

public interface TutorRepository extends JpaRepository<Tutor, UUID> {

    Optional<Tutor> findByUser_Id(UUID userId);
}
