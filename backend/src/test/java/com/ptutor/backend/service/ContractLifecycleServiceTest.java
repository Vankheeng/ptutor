package com.ptutor.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.event.TutorScoreRecalculationRequestedEvent;
import com.ptutor.backend.repository.ContractRepository;

@ExtendWith(MockitoExtension.class)
class ContractLifecycleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 0, 0);

    @Mock ContractRepository contractRepository;
    @Mock ContractTimeProvider contractTimeProvider;
    @Mock ApplicationEventPublisher eventPublisher;

    private ContractLifecycleService service;

    @BeforeEach
    void setUp() {
        when(contractTimeProvider.today()).thenReturn(TODAY);
        when(contractTimeProvider.now()).thenReturn(NOW);
        service = new ContractLifecycleService(contractRepository, contractTimeProvider, eventPublisher);
    }

    @Test
    void completesActiveAndCancelsPendingContractsAtStartOfEndDate() {
        service.processExpiredContracts();

        verify(contractRepository).completeActiveContractsEndingOnOrBefore(
                TODAY, NOW, ContractStatus.ACTIVE, ContractStatus.COMPLETED);
        verify(contractRepository).cancelPendingContractsEndingOnOrBefore(
                TODAY, NOW, ContractStatus.PENDING, ContractStatus.CANCELLED);
    }

    @Test
    void requestsScoreRecalculationForTutorsOfCompletedContracts() {
        UUID firstTutorId = UUID.randomUUID();
        UUID secondTutorId = UUID.randomUUID();
        when(contractRepository.findTutorIdsOfActiveContractsEndingOnOrBefore(TODAY, ContractStatus.ACTIVE))
                .thenReturn(List.of(firstTutorId, secondTutorId));

        service.processExpiredContracts();

        // The tutors must be read before the bulk update turns their contracts COMPLETED.
        InOrder order = inOrder(contractRepository, eventPublisher);
        order.verify(contractRepository)
                .findTutorIdsOfActiveContractsEndingOnOrBefore(TODAY, ContractStatus.ACTIVE);
        order.verify(contractRepository).completeActiveContractsEndingOnOrBefore(
                TODAY, NOW, ContractStatus.ACTIVE, ContractStatus.COMPLETED);
        order.verify(eventPublisher).publishEvent(
                new TutorScoreRecalculationRequestedEvent(Set.of(firstTutorId, secondTutorId)));
    }

    @Test
    void doesNotRequestScoreRecalculationWhenNoContractIsCompleted() {
        service.processExpiredContracts();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
