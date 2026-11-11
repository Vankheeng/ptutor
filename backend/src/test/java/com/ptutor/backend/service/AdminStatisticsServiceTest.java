package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.ptutor.backend.dto.enums.StatisticsGranularity;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AdminStatisticsQueryRepository;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceTest {

    @Mock AdminStatisticsQueryRepository repository;

    private AdminStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsService(
                repository,
                Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> service.overview(
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 1),
                "Asia/Ho_Chi_Minh", StatisticsGranularity.DAY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_STATISTICS_DATE_RANGE"));
    }

    @Test
    void rejectsRangeLongerThanTwoYears() {
        assertThatThrownBy(() -> service.overview(
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1),
                "Asia/Ho_Chi_Minh", StatisticsGranularity.MONTH))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("STATISTICS_DATE_RANGE_TOO_LARGE"));
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThatThrownBy(() -> service.overview(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "Mars/Olympus", StatisticsGranularity.DAY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_STATISTICS_TIMEZONE"));
    }

    @Test
    void comparesMetricsWithPreviousPeriod() {
        when(repository.metrics(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of("employees", BigDecimal.valueOf(15)))
                .thenReturn(Map.of("employees", BigDecimal.TEN));
        when(repository.breakdown(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        var response = service.employees(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "Asia/Ho_Chi_Minh", StatisticsGranularity.DAY);

        assertThat(response.metrics().get("employees").value()).isEqualByComparingTo("15");
        assertThat(response.metrics().get("employees").previousValue()).isEqualByComparingTo("10");
        assertThat(response.metrics().get("employees").changePercent()).isEqualByComparingTo("50.00");
        assertThat(response.range().previousFromDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(response.range().previousToDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }
}
