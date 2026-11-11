package com.ptutor.backend.dto.response;

import java.time.LocalDate;

import com.ptutor.backend.dto.enums.StatisticsGranularity;

public record StatisticsRangeResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String timezone,
        StatisticsGranularity granularity,
        LocalDate previousFromDate,
        LocalDate previousToDate) {
}
