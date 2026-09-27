package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record AdminTutorLessonResponse(
        UUID lessonId,
        UUID contractId,
        ContractStatus contractStatus,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        UUID studentUserId,
        String studentName,
        String studentEmail,
        String title,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        TeachingMode teachingMode,
        String meetingLink,
        String location,
        String materialsUrl,
        LessonStatus status,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
