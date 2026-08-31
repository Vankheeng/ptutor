package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByUser_Id(UUID userId);
}
