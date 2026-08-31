package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUser_Id(UUID userId);
}
