package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Contract;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
}
