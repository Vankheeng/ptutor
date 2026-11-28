package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    interface RatingCount {
        Integer getRating();

        long getReviewCount();
    }

    boolean existsByContract_Id(UUID contractId);

    @EntityGraph(attributePaths = { "student", "student.user", "tutor", "tutor.user" })
    List<Review> findAllByRatingOrderByCreatedAtDesc(Integer rating, Pageable pageable);

    @EntityGraph(attributePaths = { "student", "student.user", "tutor", "tutor.user" })
    @org.springframework.data.jpa.repository.Query("""
            select review
            from Review review
            where review.tutor.user.id = :userId
              and (:rating is null or review.rating = :rating)
            """)
    Page<Review> findAllForTutorUser(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("rating") Integer rating,
            Pageable pageable);

    @Query("""
            select review.rating as rating, count(review) as reviewCount
            from Review review
            where review.tutor.id = :tutorId
              and review.rating between 1 and 5
            group by review.rating
            """)
    List<RatingCount> countRatingsByTutorId(@Param("tutorId") UUID tutorId);

    @EntityGraph(attributePaths = { "student", "student.user", "tutor", "tutor.user" })
    @Query("""
            select review
            from Review review
            where review.tutor.id = :tutorId
              and review.comment is not null
              and trim(review.comment) <> ''
            """)
    Page<Review> findCommentedByTutorId(@Param("tutorId") UUID tutorId, Pageable pageable);
}
