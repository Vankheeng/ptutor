package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

public record StudyingRequestResponse(
        UUID id,
        UUID studentId,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        UUID districtId,
        String districtName,
        String title,
        String description,
        String note,
        String detailAddress,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String learningGoals,
        LearningMode learningMode,
        String preferredSchedule,
        List<Availability> availabilities,
        RequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Availability(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
