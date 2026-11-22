package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;

@SpringBootTest
@Transactional
class AdminTransactionRepositoryIntegrationTest {
    @Autowired AdminTransactionRepository repository;
    @Autowired EntityManager entityManager;

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

    @Test
    void exposesPaymentCodeWhenBusinessReferenceIsAbsent() {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                insert into users (id, citizen_id, email, password, status)
                values (:id, :citizenId, :email, 'unused', 'ACTIVE')
                """)
                .setParameter("id", userId)
                .setParameter("citizenId", userId.toString())
                .setParameter("email", userId + "@example.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                insert into payments (id, user_id, amount, payment_method, payment_type,
                                      status, transaction_code, paid_at)
                values (:id, :userId, 10000, 'VNPAY', 'TEACHING_REQUEST_FEE',
                        'PAID', :transactionCode, now())
                """)
                .setParameter("id", paymentId)
                .setParameter("userId", userId)
                .setParameter("transactionCode", "TEST-" + paymentId)
                .executeUpdate();

        var transaction = repository.findById(FinancialTransactionSource.PAYMENT, paymentId).orElseThrow();
        assertThat(transaction.transactionCode()).isEqualTo("TEST-" + paymentId);
        assertThat(transaction.transaction().referenceId()).isNull();
    }
}
