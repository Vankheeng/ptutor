package com.ptutor.backend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ptutor.backend.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationDomainEvent event) {
        try {
            notificationService.createFromEvent(event);
        } catch (Exception exception) {
            log.warn("Unable to persist notification for event {} and recipient {}",
                    event.eventType(), event.recipientUserId(), exception);
        }
    }
}
