package com.ptutor.backend.tutor.dto;

import java.time.LocalTime;

public record TeachingRequestAvailabilityResponse(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
