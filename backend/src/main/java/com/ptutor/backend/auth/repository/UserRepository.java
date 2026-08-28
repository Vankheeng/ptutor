package com.ptutor.backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ptutor.backend.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = "SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1", nativeQuery = true)
    Optional<User> findAnyByEmailIgnoreCase(String email);
}
