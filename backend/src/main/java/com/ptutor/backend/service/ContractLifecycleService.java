package com.ptutor.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContractLifecycleService {

    private final ContractRepository contractRepository;
    private final ContractTimeProvider contractTimeProvider;

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
        contractRepository.completeActiveContractsEndingOnOrBefore(
                today, now, ContractStatus.ACTIVE, ContractStatus.COMPLETED);
        contractRepository.cancelPendingContractsEndingOnOrBefore(
                today, now, ContractStatus.PENDING, ContractStatus.CANCELLED);
    }
}
