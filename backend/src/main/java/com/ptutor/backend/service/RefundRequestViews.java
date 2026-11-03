package com.ptutor.backend.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.response.RefundRequestResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the refund request view shared by the student, tutor and reviewer APIs.
 */
@Component
@RequiredArgsConstructor
public class RefundRequestViews {

    private final RefundRequestLessonRepository refundRequestLessonRepository;
    private final EvidenceRepository evidenceRepository;

    public List<RefundRequestLesson> disputedLessons(RefundRequest request) {
        return refundRequestLessonRepository
                .findAllByRefundRequest_IdOrderByLesson_DateAscLesson_StartTimeAsc(request.getId());
    }

    public List<RefundRequestResponse.DisputedLesson> toDisputedLessons(List<RefundRequestLesson> disputed) {
        return disputed.stream().map(item -> {
            Lesson lesson = item.getLesson();
            return new RefundRequestResponse.DisputedLesson(
                    lesson.getId(), lesson.getTitle(), lesson.getDate(), lesson.getStartTime(), lesson.getEndTime(),
                    lesson.getMarkedTaughtAt(), item.getClaimedOutcome(), item.getTutorPosition(), item.getDecision());
        }).toList();
    }

    public List<RefundRequestResponse.Evidence> evidences(RefundRequest request) {
        return evidenceRepository.findAllByRefundRequest_IdOrderByCreatedAtAsc(request.getId()).stream()
                .map(this::toEvidence)
                .toList();
    }

    public RefundRequestResponse toResponse(RefundRequest request) {
        Contract contract = request.getContract();
        User student = contract.getStudent().getUser();
        User tutor = contract.getTutor().getUser();
        return new RefundRequestResponse(
                request.getId(), contract.getId(),
                contract.getSubject() == null ? null : contract.getSubject().getName(),
                contract.getGrade() == null ? null : contract.getGrade().getName(),
                student.getId(), fullName(student), tutor.getId(), fullName(tutor),
                request.getStatus(), request.getDescription(),
                toDisputedLessons(disputedLessons(request)), evidences(request),
                request.getTutorResponse(), request.getTutorRespondedAt(), request.getTutorResponseDeadline(),
                request.getRefundAmount(), request.getTutorShareAmount(), request.getRejectionReason(),
                request.getReviewedAt(),
                request.getCreatedAt(), request.getUpdatedAt());
    }

    static String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private RefundRequestResponse.Evidence toEvidence(Evidence evidence) {
        return new RefundRequestResponse.Evidence(
                evidence.getId(), evidence.getFileUrl(), evidence.getFileType(),
                evidence.getUploadedBy() == null ? null : evidence.getUploadedBy().getId());
    }
}
