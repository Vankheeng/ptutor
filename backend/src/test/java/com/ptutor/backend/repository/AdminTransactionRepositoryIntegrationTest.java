package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;

@SpringBootTest
@Transactional
class AdminTransactionRepositoryIntegrationTest {
    @Autowired AdminTransactionRepository repository;

    @Test
    void queriesAllTransactionSourcesWithFiltersAndPagination() {
        var result = repository.findAll(FinancialTransactionSource.PAYMENT, null, "PAID", null, "VNPAY",
                "unlikely-user-" + UUID.randomUUID(), LocalDate.of(2025, 1, 1).atStartOfDay(),
                LocalDate.of(2026, 1, 1).atStartOfDay(), PageRequest.of(0, 20));
        assertThat(result).isEmpty();
        assertThat(repository.findById(FinancialTransactionSource.WITHDRAWAL, UUID.randomUUID())).isEmpty();
        var firstPage = repository.findAll(null, null, null, null, null, null, null, null, PageRequest.of(0, 5));
        assertThat(firstPage.getContent()).allSatisfy(item -> {
            assertThat(item.userEmail()).isNotBlank();
            assertThat(item.purpose()).isNotBlank();
            assertThat(repository.findById(item.transaction().source(), item.transaction().id())).contains(item);
        });
    }
}
