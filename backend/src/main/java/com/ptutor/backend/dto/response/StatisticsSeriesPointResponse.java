package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatisticsSeriesPointResponse(LocalDate period, BigDecimal value) {
}
