package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByNameIgnoreCase(String name);

    List<Subject> findAllByStatusOrderByNameAsc(CatalogStatus status);
}
