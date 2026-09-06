package com.ptutor.backend.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.repository.ContractRepository;

@ExtendWith(MockitoExtension.class)
class ContractLifecycleServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock ContractTimeProvider contractTimeProvider;

    @Test
    void completesActiveAndCancelsPendingContractsAtStartOfEndDate() {
        LocalDate today = LocalDate.of(2026, 9, 5);
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 0, 0);
        when(contractTimeProvider.today()).thenReturn(today);
        when(contractTimeProvider.now()).thenReturn(now);
        ContractLifecycleService service = new ContractLifecycleService(contractRepository, contractTimeProvider);

        service.processExpiredContracts();

        verify(contractRepository).completeActiveContractsEndingOnOrBefore(
                today, now, ContractStatus.ACTIVE, ContractStatus.COMPLETED);
        verify(contractRepository).cancelPendingContractsEndingOnOrBefore(
                today, now, ContractStatus.PENDING, ContractStatus.CANCELLED);
    }
}
