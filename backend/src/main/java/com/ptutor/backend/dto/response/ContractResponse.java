package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record ContractResponse(
        UUID id,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String studentEmail,
        UUID tutorId,
        String tutorFirstName,
        String tutorLastName,
        String tutorEmail,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        TeachingMode teachingMode,
        BigDecimal price,
        String paymentPeriod,
        Integer totalLessons,
        String preferredSchedule,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        UUID createdByUserId,
        UUID signedByUserId,
        UUID tutorStudentRequestId,
        UUID studentTutorRequestId,
        UUID renewedFromContractId,
        LocalDateTime signedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
