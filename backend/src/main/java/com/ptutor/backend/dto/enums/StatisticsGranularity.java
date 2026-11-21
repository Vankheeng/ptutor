package com.ptutor.backend.dto.enums;

public enum StatisticsGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlValue;

    StatisticsGranularity(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String sqlValue() {
        return sqlValue;
    }
}
