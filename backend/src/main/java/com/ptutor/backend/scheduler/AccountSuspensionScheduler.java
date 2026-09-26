package com.ptutor.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.service.UserAccessService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountSuspensionScheduler {

    private final UserAccessService userAccessService;

    @Scheduled(fixedDelayString = "${app.account-suspension.expiration-interval-ms:60000}")
    public void reactivateExpiredAccounts() {
        userAccessService.reactivateExpiredAccounts();
    }
}
