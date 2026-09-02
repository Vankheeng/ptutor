package com.ptutor.backend.tutor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record TutorStudentRequestResponse(
        UUID id,
        UUID tutorId,
        UUID studyingRequestId,
        String studyingRequestTitle,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        BigDecimal proposedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TutorStudentRequestResponse from(TutorStudentRequest request) {
        return new TutorStudentRequestResponse(
                request.getId(),
                request.getTutor().getId(),
                request.getStudyingRequest().getId(),
                request.getStudyingRequest().getTitle(),
                request.getStudyingRequest().getSubject().getId(),
                request.getStudyingRequest().getSubject().getName(),
                request.getGrade().getId(),
                request.getGrade().getName(),
                request.getProposedPrice(),
                request.getTeachingMode(),
                request.getPreferredSchedule(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
