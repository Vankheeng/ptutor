package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Grade;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    Optional<Grade> findByNameIgnoreCase(String name);
}
