package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;

@ExtendWith(MockitoExtension.class)
class PaymentInstallmentReminderServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock ContractPaymentInstallmentService installmentService;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private PaymentInstallmentReminderService service;

    @BeforeEach
    void setUp() {
        service = new PaymentInstallmentReminderService(
                contractRepository,
                installmentService,
                installmentRepository,
                eventPublisher,
                Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Ho_Chi_Minh");
    }

    @Test
    void publishesReminderOnlyForInstallmentsDueToday() {
        User studentUser = User.builder().email("student@example.com").build();
        studentUser.setId(UUID.randomUUID());
        Student student = Student.builder().user(studentUser).build();
        Contract contract = Contract.builder()
                .student(student)
                .status(ContractStatus.ACTIVE)
                .paymentPeriod(PaymentPeriod.MONTHLY)
                .price(BigDecimal.valueOf(200_000))
                .build();
        contract.setId(UUID.randomUUID());
        ContractPaymentInstallment installment = ContractPaymentInstallment.builder()
                .contract(contract)
                .sequenceNumber(1)
                .paymentPeriod(PaymentPeriod.MONTHLY)
                .amount(BigDecimal.valueOf(200_000))
                .dueDate(LocalDate.of(2026, 10, 1))
                .status(PaymentInstallmentStatus.PENDING)
                .build();
        installment.setId(UUID.randomUUID());

        when(contractRepository.findAllByStatus(ContractStatus.ACTIVE)).thenReturn(List.of(contract));
        when(installmentRepository.findDueInstallments(
                PaymentInstallmentStatus.PENDING,
                LocalDate.of(2026, 10, 1),
                ContractStatus.ACTIVE)).thenReturn(List.of(installment));

        service.publishDuePaymentReminders();

        verify(installmentService).ensureForActiveContract(contract);
        ArgumentCaptor<NotificationDomainEvent> eventCaptor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(NotificationEventType.PAYMENT_INSTALLMENT_DUE);
        assertThat(eventCaptor.getValue().recipientUserId()).isEqualTo(studentUser.getId());
        assertThat(eventCaptor.getValue().deduplicationKey())
                .isEqualTo("PAYMENT_INSTALLMENT_DUE:" + installment.getId());
    }
}
