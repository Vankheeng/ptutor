package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @EntityGraph(attributePaths = { "student", "student.user", "tutor", "tutor.user" })
    List<Review> findAllByRatingOrderByCreatedAtDesc(Integer rating, Pageable pageable);
}
