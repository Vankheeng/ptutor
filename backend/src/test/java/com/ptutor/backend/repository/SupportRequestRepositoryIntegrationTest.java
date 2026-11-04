package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.SupportRequestStatus;

/** Chạy các truy vấn của yêu cầu hỗ trợ trên PostgreSQL thật (migration V36, JPQL có tham số null). */
@SpringBootTest
@Transactional
class SupportRequestRepositoryIntegrationTest {

    @Autowired SupportRequestRepository supportRequestRepository;

    @Test
    void executesSupportRequestQueriesAgainstPostgres() {
        UUID missing = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        var pageable = PageRequest.of(0, 20);

        assertThat(supportRequestRepository.nextCodeNumber()).isPositive();
        assertThat(supportRequestRepository.findMine(missing, null, pageable)).isEmpty();
        assertThat(supportRequestRepository.findMine(missing, SupportRequestStatus.OPEN, pageable)).isEmpty();
        assertThat(supportRequestRepository.findQueue(EnumSet.of(SupportRequestStatus.OPEN), null, null, null, null,
                pageable)).isEmpty();
        assertThat(supportRequestRepository.findQueue(EnumSet.allOf(SupportRequestStatus.class), missing, "STUDENT",
                now.minusDays(1), now, pageable)).isEmpty();
        assertThat(supportRequestRepository.countByUser_IdAndStatus(missing, SupportRequestStatus.OPEN)).isZero();
        assertThat(supportRequestRepository.countByUser_IdAndCreatedAtAfter(missing, now.minusDays(1))).isZero();
        assertThat(supportRequestRepository.countByStatus()).isNotNull();
        assertThat(supportRequestRepository.countBySource()).isNotNull();
        assertThat(supportRequestRepository.countBySourceSince(LocalDateTime.now().minusDays(30))).isNotNull();
        assertThat(supportRequestRepository.findAnsweredSince(now.minusDays(30))).isNotNull();
        assertThat(supportRequestRepository.closeAnsweredBefore(now.minusDays(7), now,
                SupportRequestStatus.ANSWERED, SupportRequestStatus.CLOSED)).isGreaterThanOrEqualTo(0);
        assertThat(supportRequestRepository.purgeExcerptsClosedBefore(now.minusDays(365), now))
                .isGreaterThanOrEqualTo(0);
    }
}
