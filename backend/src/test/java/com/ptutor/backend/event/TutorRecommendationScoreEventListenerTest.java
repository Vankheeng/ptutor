package com.ptutor.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.scheduler.AggregateRefreshJob;
import com.ptutor.backend.service.TutorRecommendationScoreService;

@ExtendWith(MockitoExtension.class)
class TutorRecommendationScoreEventListenerTest {

    @Mock TutorRepository tutorRepository;
    @Mock ContractRepository contractRepository;
    @Mock StudentTutorRequestRepository studentTutorRequestRepository;
    @Mock TutorRecommendationScoreService scoreService;
    @Mock AggregateRefreshJob aggregateRefreshJob;
    @Mock PlatformTransactionManager transactionManager;

    private TutorRecommendationScoreEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TutorRecommendationScoreEventListener(
                tutorRepository, contractRepository, studentTutorRequestRepository, scoreService,
                aggregateRefreshJob, transactionManager);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationEventType.class,
            names = { "STUDENT_REQUEST_ACCEPTED", "STUDENT_REQUEST_REJECTED" })
    void recalculatesTutorWhenTutorDecidesStudentRequest(NotificationEventType eventType) {
        UUID requestId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        when(studentTutorRequestRepository.findTutorIdByRequestId(requestId)).thenReturn(Optional.of(tutorId));

        listener.handle(NotificationDomainEvent.of(
                UUID.randomUUID(), eventType, NotificationReferenceType.STUDENT_TUTOR_REQUEST,
                requestId, Map.of()));

        verify(scoreService).recalculateByTutorId(tutorId);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationEventType.class,
            names = { "TUTOR_REQUEST_ACCEPTED", "TUTOR_REQUEST_REJECTED", "STUDENT_REQUEST_RECEIVED" })
    void ignoresEventsThatDoNotAffectTutorScore(NotificationEventType eventType) {
        listener.handle(NotificationDomainEvent.of(
                UUID.randomUUID(), eventType, NotificationReferenceType.STUDENT_TUTOR_REQUEST,
                UUID.randomUUID(), Map.of()));

        verifyNoInteractions(scoreService, aggregateRefreshJob, transactionManager);
    }

    @Test
    void recalculatesTutorOfContractEvent() {
        UUID contractId = UUID.randomUUID();
        UUID tutorUserId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        Tutor tutor = new Tutor();
        tutor.setId(tutorId);
        when(contractRepository.findTutorUserIdByContractId(contractId)).thenReturn(Optional.of(tutorUserId));
        when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor));

        listener.handle(NotificationDomainEvent.of(
                UUID.randomUUID(), NotificationEventType.CONTRACT_SIGNED, NotificationReferenceType.CONTRACT,
                contractId, Map.of()));

        verify(scoreService).recalculateByTutorId(tutorId);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationEventType.class,
            names = { "COMPLAINT_RESOLVED", "COMPLAINT_AGAINST_YOU_RESOLVED" })
    void recalculatesTutorWhoReceivesAComplaintConclusion(NotificationEventType eventType) {
        UUID tutorUserId = UUID.randomUUID();
        Tutor tutor = new Tutor();
        tutor.setId(UUID.randomUUID());
        when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor));

        listener.handle(NotificationDomainEvent.of(
                tutorUserId, eventType, NotificationReferenceType.COMPLAINT, UUID.randomUUID(), Map.of()));

        verify(scoreService).recalculateByTutorId(tutor.getId());
    }

    @ParameterizedTest
    @EnumSource(value = NotificationEventType.class,
            names = { "COMPLAINT_REJECTED", "COMPLAINT_AGAINST_YOU_REJECTED", "COMPLAINT_RECEIVED_AGAINST_YOU" })
    void ignoresComplaintEventsWithoutAFaultConclusion(NotificationEventType eventType) {
        listener.handle(NotificationDomainEvent.of(
                UUID.randomUUID(), eventType, NotificationReferenceType.COMPLAINT, UUID.randomUUID(), Map.of()));

        verifyNoInteractions(tutorRepository, scoreService, aggregateRefreshJob, transactionManager);
    }

    @Test
    void skipsRecipientEventWhenRecipientIsNotTutor() {
        UUID userId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        listener.handle(NotificationDomainEvent.of(
                userId, NotificationEventType.ACCOUNT_SUSPENDED, null, null, Map.of()));

        verifyNoInteractions(scoreService, aggregateRefreshJob, transactionManager);
    }

    @Test
    void recalculatesEveryTutorOfRecalculationRequest() {
        UUID firstTutorId = UUID.randomUUID();
        UUID secondTutorId = UUID.randomUUID();

        listener.handle(new TutorScoreRecalculationRequestedEvent(Set.of(firstTutorId, secondTutorId)));

        verify(scoreService).recalculateByTutorId(firstTutorId);
        verify(scoreService).recalculateByTutorId(secondTutorId);
    }

    @Test
    void refreshesAggregatesThenRecalculatesInItsOwnNewTransaction() {
        UUID tutorId = UUID.randomUUID();
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);

        listener.handle(new TutorScoreRecalculationRequestedEvent(Set.of(tutorId)));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder order = inOrder(transactionManager, aggregateRefreshJob, scoreService);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(aggregateRefreshJob).refreshTutor(tutorId);
        order.verify(scoreService).recalculateByTutorId(tutorId);
        order.verify(transactionManager).commit(status);
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void failureForOneTutorDoesNotStopTheOthers() {
        UUID failingTutorId = UUID.randomUUID();
        UUID healthyTutorId = UUID.randomUUID();
        doThrow(new IllegalStateException("boom")).when(scoreService).recalculateByTutorId(failingTutorId);

        assertThatCode(() -> listener.handle(new TutorScoreRecalculationRequestedEvent(
                new LinkedHashSet<>(List.of(failingTutorId, healthyTutorId))))).doesNotThrowAnyException();

        verify(scoreService).recalculateByTutorId(healthyTutorId);
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void swallowsFailureOfNotificationEvent() {
        UUID requestId = UUID.randomUUID();
        when(studentTutorRequestRepository.findTutorIdByRequestId(requestId))
                .thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> listener.handle(NotificationDomainEvent.of(
                UUID.randomUUID(), NotificationEventType.STUDENT_REQUEST_ACCEPTED,
                NotificationReferenceType.STUDENT_TUTOR_REQUEST, requestId, Map.of())))
                .doesNotThrowAnyException();

        verifyNoInteractions(scoreService);
    }
}
