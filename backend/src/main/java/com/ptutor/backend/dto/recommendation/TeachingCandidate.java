package com.ptutor.backend.dto.recommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;

/** An OPEN teaching posting that passed the hard filters, with everything needed to score it. */
public record TeachingCandidate(
        UUID teachingRequestId,
        UUID tutorId,
        UUID tutorUserId,
        String title,
        BigDecimal expectedPrice,
        TeachingMode teachingMode,
        LocalDateTime createdAt,
        /** Cosine similarity with the query vector; null when either side has no embedding. */
        Double cosine,
        /** tutors.recommendation_score on a 0-100 scale; null when never computed. */
        BigDecimal tutorScore,
        String tutorDisplayName,
        String tutorAvatarUrl,
        BigDecimal tutorAverageRating,
        Integer tutorTotalReviews,
        List<District> districts,
        List<TimeSlot> availabilities,
        /** TOPIC / GOAL tags with role STRENGTH, from the posting and the tutor profile. */
        Set<CandidateTag> strengthTags,
        /** STYLE tag codes with role OFFERED. */
        Set<String> offeredStyleCodes,
        /** SEGMENT tag codes with role TARGET. */
        Set<String> targetSegmentCodes,
        /** The tutor rejected an application of this student (any posting) recently. */
        boolean rejectedStudentRecently,
        /** Places still free on the posting: quantity minus accepted applications, at least 1. */
        int remainingSlots,
        /** Applications waiting for the tutor's decision on this posting. */
        int pendingApplications,
        /** What the tutor decided on applications to any of their postings, recent ones only. */
        List<PastDecision> tutorDecisions,
        /** The tutor already sent a pending proposal to an OPEN studying posting of this student. */
        boolean tutorInvitedStudent) {

    public record District(UUID id, String name, UUID provinceId) {
    }

    public record CandidateTag(UUID tagId, UUID parentId) {
    }

    /** One accepted or rejected application, described by what it has in common with a new student. */
    public record PastDecision(Integer gradeLevel, BigDecimal proposedPrice, LearningMode learningMode,
            boolean accepted) {
    }
}
