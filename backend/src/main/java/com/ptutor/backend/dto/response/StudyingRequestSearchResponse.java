package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

public record StudyingRequestSearchResponse(
        UUID id,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        UUID districtId,
        String districtName,
        Integer quantity,
        String title,
        String description,
        String learningGoals,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LearningMode learningMode,
        String preferredSchedule,
        RequestStatus status,
        List<Availability> availabilities,
        LocalDateTime createdAt) {

    public record Availability(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) { }
}
