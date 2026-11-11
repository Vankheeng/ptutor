package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.StatisticsGranularity;
import com.ptutor.backend.service.AdminStatisticsService;

@SpringBootTest
@Transactional
class AdminStatisticsRepositoryIntegrationTest {

    @Autowired AdminStatisticsService service;

    @Test
    void executesAllStatisticsReportsAgainstPostgres() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        String timezone = "Asia/Ho_Chi_Minh";
        StatisticsGranularity granularity = StatisticsGranularity.DAY;

        assertThat(service.overview(from, to, timezone, granularity).report()).isEqualTo("OVERVIEW");
        assertThat(service.students(from, to, timezone, granularity).report()).isEqualTo("STUDENTS");
        assertThat(service.tutors(from, to, timezone, granularity).report()).isEqualTo("TUTORS");
        assertThat(service.studyingRequests(from, to, timezone, granularity).report())
                .isEqualTo("STUDYING_REQUESTS");
        assertThat(service.teachingRequests(from, to, timezone, granularity).report())
                .isEqualTo("TEACHING_REQUESTS");
        assertThat(service.matching(from, to, timezone, granularity).report()).isEqualTo("MATCHING");
        assertThat(service.contracts(from, to, timezone, granularity).report()).isEqualTo("CONTRACTS");
        assertThat(service.lessons(from, to, timezone, granularity).report()).isEqualTo("LESSONS");
        assertThat(service.finance(from, to, timezone, granularity).report()).isEqualTo("FINANCE");
        assertThat(service.payments(from, to, timezone, granularity).report()).isEqualTo("PAYMENTS");
        assertThat(service.installments(from, to, timezone, granularity).report()).isEqualTo("INSTALLMENTS");
        assertThat(service.wallets(from, to, timezone, granularity).report()).isEqualTo("WALLETS");
        assertThat(service.withdrawals(from, to, timezone, granularity).report()).isEqualTo("WITHDRAWALS");
        assertThat(service.employees(from, to, timezone, granularity).report()).isEqualTo("EMPLOYEES");
    }
}
