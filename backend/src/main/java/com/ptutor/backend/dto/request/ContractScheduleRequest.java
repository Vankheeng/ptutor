package com.ptutor.backend.dto.request;

import java.time.LocalTime;

public record ContractScheduleRequest(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
