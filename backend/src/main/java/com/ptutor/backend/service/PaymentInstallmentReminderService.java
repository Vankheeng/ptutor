package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentInstallmentReminderService {

    private final ContractRepository contractRepository;
    private final ContractPaymentInstallmentService installmentService;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.notification.time-zone:Asia/Ho_Chi_Minh}")
    private String timeZone;

    @Scheduled(
            cron = "${app.notification.payment-reminder-cron:0 0 9 * * *}",
            zone = "${app.notification.time-zone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void publishDuePaymentReminders() {
        for (var contract : contractRepository.findAllByStatus(ContractStatus.ACTIVE)) {
            installmentService.ensureForActiveContract(contract);
        }

        LocalDate dueDate = LocalDate.now(clock.withZone(ZoneId.of(timeZone))).plusDays(3);
        for (ContractPaymentInstallment installment : installmentRepository.findDueInstallments(
                PaymentInstallmentStatus.PENDING, dueDate, ContractStatus.ACTIVE)) {
            eventPublisher.publishEvent(NotificationDomainEvent.deduplicated(
                    installment.getContract().getStudent().getUser().getId(),
                    NotificationEventType.PAYMENT_INSTALLMENT_DUE,
                    NotificationReferenceType.INSTALLMENT,
                    installment.getId(),
                    java.util.Map.of(),
                    "PAYMENT_INSTALLMENT_DUE:" + installment.getId()));
        }
    }
}
