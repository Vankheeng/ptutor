package com.ptutor.backend.tutor.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.enums.CertificateStatus;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<Certificate> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(UUID tutorId, CertificateStatus status);

    Optional<Certificate> findByIdAndTutor_Id(UUID id, UUID tutorId);
}
