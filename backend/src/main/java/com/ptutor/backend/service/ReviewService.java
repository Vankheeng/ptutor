package com.ptutor.backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ReviewCreateRequest;
import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.dto.response.ReviewResponse;
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
