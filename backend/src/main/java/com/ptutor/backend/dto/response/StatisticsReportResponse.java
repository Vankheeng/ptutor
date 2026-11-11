package com.ptutor.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StatisticsReportResponse(
        String report,
        StatisticsRangeResponse range,
        Map<String, StatisticsMetricResponse> metrics,
        Map<String, List<StatisticsBucketResponse>> breakdowns,
        Map<String, List<StatisticsSeriesPointResponse>> series,
        Instant generatedAt) {
}
