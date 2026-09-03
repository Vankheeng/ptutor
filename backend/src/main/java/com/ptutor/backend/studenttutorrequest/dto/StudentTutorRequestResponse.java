package com.ptutor.backend.studenttutorrequest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.LearningMode;

public record StudentTutorRequestResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID tutorId,
        UUID teachingRequestId,
        String teachingRequestTitle,
        UUID gradeId,
        String gradeName,
        BigDecimal proposedPrice,
        LearningMode learningMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static StudentTutorRequestResponse from(StudentTutorRequest request) {
        String firstName = request.getStudent().getUser().getFirstName();
        String lastName = request.getStudent().getUser().getLastName();
        String studentName = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).strip();
        return new StudentTutorRequestResponse(
                request.getId(),
                request.getStudent().getId(),
                studentName.strip(),
                request.getTeachingRequest().getTutor().getId(),
                request.getTeachingRequest().getId(),
                request.getTeachingRequest().getTitle(),
                request.getGrade().getId(),
                request.getGrade().getName(),
                request.getProposedPrice(),
                request.getLearningMode(),
                request.getPreferredSchedule(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
