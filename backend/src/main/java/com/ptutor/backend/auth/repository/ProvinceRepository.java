package com.ptutor.backend.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Province;

public interface ProvinceRepository extends JpaRepository<Province, UUID> {
}
