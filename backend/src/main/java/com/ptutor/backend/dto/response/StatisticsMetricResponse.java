package com.ptutor.backend.dto.response;

import java.math.BigDecimal;

public record StatisticsMetricResponse(
        BigDecimal value,
        BigDecimal previousValue,
        BigDecimal changePercent) {
}
