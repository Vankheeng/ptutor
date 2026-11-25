package com.ptutor.backend.event;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.service.TutorRecommendationScoreService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TutorRecommendationScoreEventListener {

    private static final Logger log = LoggerFactory.getLogger(TutorRecommendationScoreEventListener.class);

    private static final Set<NotificationEventType> RECIPIENT_EVENTS = EnumSet.of(
            NotificationEventType.CERTIFICATE_APPROVED,
            NotificationEventType.CERTIFICATE_REJECTED,
            NotificationEventType.ACCOUNT_SUSPENDED,
            NotificationEventType.ACCOUNT_REACTIVATED);

    private static final Set<NotificationEventType> CONTRACT_EVENTS = EnumSet.of(
            NotificationEventType.CONTRACT_SIGNED,
            NotificationEventType.CONTRACT_REJECTED,
            NotificationEventType.CONTRACT_CANCELLED);

    private final TutorRepository tutorRepository;
    private final ContractRepository contractRepository;
    private final TutorRecommendationScoreService scoreService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationDomainEvent event) {
        try {
            if (RECIPIENT_EVENTS.contains(event.eventType())) {
                recalculateIfTutor(event.recipientUserId());
                return;
            }
            if (CONTRACT_EVENTS.contains(event.eventType())
                    && event.referenceType() == NotificationReferenceType.CONTRACT
                    && event.referenceId() != null) {
                UUID contractId = UUID.fromString(event.referenceId());
                contractRepository.findTutorUserIdByContractId(contractId).ifPresent(this::recalculateIfTutor);
            }
        } catch (Exception exception) {
            log.warn("Unable to recalculate tutor score for event {} and reference {}",
                    event.eventType(), event.referenceId(), exception);
        }
    }

    private void recalculateIfTutor(UUID userId) {
        if (userId != null && tutorRepository.findByUser_Id(userId).isPresent()) {
            scoreService.recalculateByUserId(userId);
        }
    }
}
