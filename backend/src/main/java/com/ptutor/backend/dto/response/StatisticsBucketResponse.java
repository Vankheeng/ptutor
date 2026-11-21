package com.ptutor.backend.dto.response;

import java.math.BigDecimal;

public record StatisticsBucketResponse(String key, BigDecimal value) {
}
