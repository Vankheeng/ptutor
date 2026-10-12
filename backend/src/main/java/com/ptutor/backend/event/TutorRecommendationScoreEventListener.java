package com.ptutor.backend.event;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.service.TutorRecommendationScoreService;

@Component
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

    /** The tutor decided an application sent by a student; the event recipient is the student. */
    private static final Set<NotificationEventType> STUDENT_REQUEST_DECISION_EVENTS = EnumSet.of(
            NotificationEventType.STUDENT_REQUEST_ACCEPTED,
            NotificationEventType.STUDENT_REQUEST_REJECTED);

    private final TutorRepository tutorRepository;
    private final ContractRepository contractRepository;
    private final StudentTutorRequestRepository studentTutorRequestRepository;
    private final TutorRecommendationScoreService scoreService;
    private final TransactionTemplate newTransaction;

    public TutorRecommendationScoreEventListener(
            TutorRepository tutorRepository,
            ContractRepository contractRepository,
            StudentTutorRequestRepository studentTutorRequestRepository,
            TutorRecommendationScoreService scoreService,
            PlatformTransactionManager transactionManager) {
        this.tutorRepository = tutorRepository;
        this.contractRepository = contractRepository;
        this.studentTutorRequestRepository = studentTutorRequestRepository;
        this.scoreService = scoreService;
        // AFTER_COMMIT runs while the committed transaction is still bound to the thread, so a
        // plain @Transactional call would join it and never be flushed. Each recalculation
        // therefore runs in its own transaction.
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationDomainEvent event) {
        try {
            if (RECIPIENT_EVENTS.contains(event.eventType())) {
                recalculateIfTutorUser(event.recipientUserId());
                return;
            }
            if (CONTRACT_EVENTS.contains(event.eventType())
                    && event.referenceType() == NotificationReferenceType.CONTRACT
                    && event.referenceId() != null) {
                UUID contractId = UUID.fromString(event.referenceId());
                contractRepository.findTutorUserIdByContractId(contractId).ifPresent(this::recalculateIfTutorUser);
                return;
            }
            if (STUDENT_REQUEST_DECISION_EVENTS.contains(event.eventType())
                    && event.referenceType() == NotificationReferenceType.STUDENT_TUTOR_REQUEST
                    && event.referenceId() != null) {
                UUID requestId = UUID.fromString(event.referenceId());
                studentTutorRequestRepository.findTutorIdByRequestId(requestId).ifPresent(this::recalculateTutor);
            }
        } catch (Exception exception) {
            log.warn("Unable to recalculate tutor score for event {} and reference {}",
                    event.eventType(), event.referenceId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TutorScoreRecalculationRequestedEvent event) {
        event.tutorIds().forEach(tutorId -> {
            try {
                recalculateTutor(tutorId);
            } catch (Exception exception) {
                log.warn("Unable to recalculate score for tutor {}", tutorId, exception);
            }
        });
    }

    private void recalculateIfTutorUser(UUID userId) {
        if (userId != null && tutorRepository.findByUser_Id(userId).isPresent()) {
            newTransaction.executeWithoutResult(status -> scoreService.recalculateByUserId(userId));
        }
    }

    private void recalculateTutor(UUID tutorId) {
        newTransaction.executeWithoutResult(status -> scoreService.recalculateByTutorId(tutorId));
    }
}
