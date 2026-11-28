package com.ptutor.backend.dto.recommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.entity.enums.LearningMode;

/** An OPEN studying posting that passed the hard filters, with everything needed to score it. */
public record StudyingCandidate(
        UUID studyingRequestId,
        UUID studentId,
        UUID studentUserId,
        String title,
        String learningGoals,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LearningMode learningMode,
        String gradeName,
        /** Where the student wants to learn; null when not given. */
        District district,
        LocalDateTime createdAt,
        /** Cosine similarity with the tutor's supply vector; null when either side has no embedding. */
        Double cosine,
        List<TimeSlot> availabilities,
        /** TOPIC / GOAL tags the student needs, from the posting and the student profile. */
        List<WeightedTag> needTags,
        /** STYLE tag codes the student prefers. */
        Set<String> preferredStyleCodes,
        /** SEGMENT tag code describing the student's level; null when unknown. */
        String levelCode,
        /** The student rejected a proposal of this tutor (any posting) recently. */
        boolean rejectedTutorRecently,
        /** Tutors still wanted: always 1, a studying posting takes one tutor and is MATCHED once it has one. */
        int remainingSlots,
        /** Proposals waiting for the student's decision on this posting. */
        int pendingProposals,
        /** What the student decided on proposals to any of their postings, recent ones only. */
        List<PastProposal> studentDecisions,
        /** The student already sent a pending application to an OPEN teaching posting of this tutor. */
        boolean studentAppliedToTutor) {

    /** One accepted or rejected proposal, described by what it has in common with a new tutor. */
    public record PastProposal(BigDecimal proposedPrice, Double tutorQuality, boolean accepted) {
    }
}
