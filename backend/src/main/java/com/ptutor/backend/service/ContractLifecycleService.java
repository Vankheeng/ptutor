package com.ptutor.backend.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.event.TutorScoreRecalculationRequestedEvent;
import com.ptutor.backend.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContractLifecycleService {

    private final ContractRepository contractRepository;
    private final ContractTimeProvider contractTimeProvider;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * At the beginning of endDate, active contracts are completed and unsigned
     * contracts are cancelled. Both operations are conditional bulk updates,
     * so concurrent application instances remain idempotent.
     */
    @Scheduled(
            cron = "${app.contract.lifecycle.expiration-cron:0 0 0 * * *}",
            zone = "${app.contract.lifecycle.time-zone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void processExpiredContracts() {
        var today = contractTimeProvider.today();
        var now = contractTimeProvider.now();
        // Read before the bulk update: afterwards the affected tutors can no longer be told apart.
        Set<UUID> completedTutorIds = Set.copyOf(contractRepository
                .findTutorIdsOfActiveContractsEndingOnOrBefore(today, ContractStatus.ACTIVE));
        contractRepository.completeActiveContractsEndingOnOrBefore(
                today, now, ContractStatus.ACTIVE, ContractStatus.COMPLETED);
        if (!completedTutorIds.isEmpty()) {
            eventPublisher.publishEvent(new TutorScoreRecalculationRequestedEvent(completedTutorIds));
        }
        contractRepository.cancelPendingContractsEndingOnOrBefore(
                today, now, ContractStatus.PENDING, ContractStatus.CANCELLED);
    }
}
