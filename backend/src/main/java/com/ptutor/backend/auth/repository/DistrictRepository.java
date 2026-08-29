package com.ptutor.backend.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.District;

public interface DistrictRepository extends JpaRepository<District, UUID> {
}
