package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.response.StatisticsBucketResponse;
import com.ptutor.backend.dto.response.StatisticsSeriesPointResponse;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminStatisticsQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<String, BigDecimal> metrics(String sql, MapSqlParameterSource parameters) {
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, parameters);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key, decimal(value)));
        return result;
    }

    public List<StatisticsBucketResponse> breakdown(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new StatisticsBucketResponse(
                resultSet.getString("bucket"), decimal(resultSet.getObject("value"))));
    }

    public List<StatisticsSeriesPointResponse> series(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            Object rawPeriod = resultSet.getObject("period");
            LocalDate period = rawPeriod instanceof java.sql.Date date
                    ? date.toLocalDate()
                    : ((Timestamp) rawPeriod).toLocalDateTime().toLocalDate();
            return new StatisticsSeriesPointResponse(period, decimal(resultSet.getObject("value")));
        });
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}
