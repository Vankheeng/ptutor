package com.ptutor.backend.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.mapper.ReviewMapper;
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

    @Transactional(readOnly = true)
    public List<PublicReviewResponse> findLatestPublicReviews(int requestedLimit) {
        return reviewRepository.findAllByRatingOrderByCreatedAtDesc(
                FEATURED_RATING, PageRequest.of(0, normalizeLimit(requestedLimit))).stream()
                .map(reviewMapper::toPublicResponse)
                .toList();
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
