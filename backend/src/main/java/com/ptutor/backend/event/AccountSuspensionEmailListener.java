package com.ptutor.backend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.service.EmailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountSuspensionEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountSuspensionEmailListener.class);

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationDomainEvent event) {
        if (event.eventType() != NotificationEventType.ACCOUNT_SUSPENDED
                && event.eventType() != NotificationEventType.ACCOUNT_REACTIVATED) {
            return;
        }
        String email = event.data().get("email");
        if (email == null || email.isBlank()) {
            log.warn("Account event {} for user {} has no recipient email",
                    event.eventType(), event.recipientUserId());
            return;
        }
        try {
            if (event.eventType() == NotificationEventType.ACCOUNT_SUSPENDED) {
                emailService.sendAccountSuspended(
                        email,
                        event.data().getOrDefault("reason", ""),
                        event.data().getOrDefault("type", "PERMANENT"),
                        event.data().getOrDefault("until", ""));
            } else {
                emailService.sendAccountReactivated(email);
            }
        } catch (Exception exception) {
            log.warn("Unable to send account status email for event {} and user {}",
                    event.eventType(), event.recipientUserId(), exception);
        }
    }
}
