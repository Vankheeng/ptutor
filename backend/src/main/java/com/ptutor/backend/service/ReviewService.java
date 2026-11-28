package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ReviewCreateRequest;
import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.dto.response.ReviewResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorReviewOverviewResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Review;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.event.TutorScoreRecalculationRequestedEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ReviewMapper;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final int DEFAULT_LIMIT = 3;
    public static final int MAX_LIMIT = 50;
    private static final int FEATURED_RATING = 5;

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final ContractRepository contractRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PublicReviewResponse> findLatestPublicReviews(int requestedLimit) {
        return reviewRepository.findAllByRatingOrderByCreatedAtDesc(
                FEATURED_RATING, PageRequest.of(0, normalizeLimit(requestedLimit))).stream()
                .map(reviewMapper::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TutorReviewOverviewResponse findTutorReviewOverview(UUID tutorId, Pageable pageable) {
        Map<Integer, Long> counts = new HashMap<>();
        reviewRepository.countRatingsByTutorId(tutorId)
                .forEach(item -> counts.put(item.getRating(), item.getReviewCount()));

        long totalReviews = counts.values().stream().mapToLong(Long::longValue).sum();
        long weightedTotal = counts.entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey() * entry.getValue())
                .sum();
        BigDecimal averageRating = totalReviews == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(weightedTotal)
                        .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

        List<TutorReviewOverviewResponse.RatingDistribution> distribution = new ArrayList<>(5);
        for (int rating = 5; rating >= 1; rating--) {
            long count = counts.getOrDefault(rating, 0L);
            BigDecimal percentage = totalReviews == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(count * 100L)
                            .divide(BigDecimal.valueOf(totalReviews), 1, RoundingMode.HALF_UP);
            distribution.add(new TutorReviewOverviewResponse.RatingDistribution(rating, count, percentage));
        }

        org.springframework.data.domain.Page<Review> comments =
                reviewRepository.findCommentedByTutorId(tutorId, pageable);
        List<PublicReviewResponse> commentResponses = comments.getContent().stream()
                .map(reviewMapper::toPublicResponse)
                .toList();

        return new TutorReviewOverviewResponse(
                averageRating,
                totalReviews,
                comments.getTotalElements(),
                List.copyOf(distribution),
                PageResponse.from(comments, commentResponses));
    }

    @Transactional
    public ReviewResponse createForContract(UUID userId, UUID contractId, ReviewCreateRequest request) {
        Contract contract = contractRepository.findDetailedByIdAndStudentUserId(contractId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found: " + contractId));
        if (contract.getStatus() != ContractStatus.COMPLETED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "CONTRACT_NOT_COMPLETED", "Only COMPLETED contracts can be reviewed");
        }
        if (reviewRepository.existsByContract_Id(contractId)) {
            throw reviewAlreadyExists();
        }

        String comment = request.comment() == null || request.comment().isBlank()
                ? null : request.comment().strip();
        Review review = Review.builder()
                .student(contract.getStudent())
                .tutor(contract.getTutor())
                .contract(contract)
                .rating(request.rating())
                .comment(comment)
                .build();
        Review saved;
        try {
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            // Two concurrent submissions: the unique index on contract_id lets only one through.
            throw reviewAlreadyExists();
        }
        eventPublisher.publishEvent(
                new TutorScoreRecalculationRequestedEvent(Set.of(contract.getTutor().getId())));
        return reviewMapper.toResponse(saved);
    }

    private ApiException reviewAlreadyExists() {
        return new ApiException(
                HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "This contract has already been reviewed");
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
