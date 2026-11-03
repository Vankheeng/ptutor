package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.ContractSchedule;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;

@ExtendWith(MockitoExtension.class)
class ContractPaymentInstallmentServiceTest {

    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock ContractTimeProvider contractTimeProvider;

    private ContractPaymentInstallmentService service;

    @BeforeEach
    void setUp() {
        service = new ContractPaymentInstallmentService(installmentRepository, contractTimeProvider);
        lenient().when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 10, 1));
    }

    @Test
    void createsWeeklyInstallmentsWithDeadlineThreeDaysBeforeFirstScheduledLesson() {
        Contract contract = activeContract(PaymentPeriod.WEEKLY,
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 30));
        contract.setSchedules(List.of(schedule(1))); // Monday
        when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of());
        when(installmentRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureForActiveContract(contract);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContractPaymentInstallment>> captor = ArgumentCaptor.forClass(List.class);
        verify(installmentRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ContractPaymentInstallment::getDueDate)
                .containsExactly(
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 9),
                        LocalDate.of(2026, 10, 16),
                        LocalDate.of(2026, 10, 23));
    }

    @Test
    void createsMonthlyInstallmentsUsingFirstScheduledLessonOfEachCycle() {
        Contract contract = activeContract(PaymentPeriod.MONTHLY,
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 12, 31));
        contract.setSchedules(List.of(schedule(1))); // Monday
        when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of());
        when(installmentRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureForActiveContract(contract);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContractPaymentInstallment>> captor = ArgumentCaptor.forClass(List.class);
        verify(installmentRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ContractPaymentInstallment::getDueDate)
                .containsExactly(
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 30),
                        LocalDate.of(2026, 12, 4));
    }

    @Test
    void assignsAndReleasesPerLessonInstallmentWithDeadlineClampedToToday() {
        Contract contract = activeContract(PaymentPeriod.PER_LESSON,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        Lesson lesson = Lesson.builder().contract(contract).date(LocalDate.of(2026, 10, 2)).build();
        lesson.setId(UUID.randomUUID());
        ContractPaymentInstallment installment = installment(contract, 1, PaymentInstallmentStatus.PENDING);

        when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of(installment));
        when(installmentRepository.findFirstByContract_IdAndStatusAndLessonIsNullOrderBySequenceNumberAsc(
                contract.getId(), PaymentInstallmentStatus.PENDING)).thenReturn(Optional.of(installment));

        service.assignNextInstallment(contract, lesson);

        assertThat(installment.getLesson()).isSameAs(lesson);
        assertThat(installment.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(installmentRepository).save(installment);

        when(installmentRepository.findByLesson_Id(lesson.getId())).thenReturn(Optional.of(installment));
        service.releasePendingLessonInstallment(lesson);

        assertThat(installment.getLesson()).isNull();
        assertThat(installment.getDueDate()).isNull();
    }

    @Test
    void preventsMarkingPerLessonAsTaughtUntilItsInstallmentIsPaid() {
        Contract contract = activeContract(PaymentPeriod.PER_LESSON,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        Lesson lesson = Lesson.builder().contract(contract).date(LocalDate.of(2026, 10, 10)).build();
        lesson.setId(UUID.randomUUID());
        ContractPaymentInstallment unpaid = installment(contract, 1, PaymentInstallmentStatus.PENDING);
        unpaid.setLesson(lesson);
        when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of(unpaid));
        when(installmentRepository.findByLesson_Id(lesson.getId())).thenReturn(Optional.of(unpaid));

        assertThatThrownBy(() -> service.requirePaidForLesson(lesson))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INSTALLMENT_PAYMENT_REQUIRED");
                });

        unpaid.setStatus(PaymentInstallmentStatus.PAID);
        service.requirePaidForLesson(lesson);
    }

    private Contract activeContract(PaymentPeriod period, LocalDate startDate, LocalDate endDate) {
        Contract contract = Contract.builder()
                .status(ContractStatus.ACTIVE)
                .paymentPeriod(period)
                .price(BigDecimal.valueOf(200_000))
                .totalLession(12)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        contract.setId(UUID.randomUUID());
        return contract;
    }

    private ContractSchedule schedule(int dayOfWeek) {
        return ContractSchedule.builder()
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0))
                .build();
    }

    private ContractPaymentInstallment installment(
            Contract contract, int sequence, PaymentInstallmentStatus status) {
        return ContractPaymentInstallment.builder()
                .contract(contract)
                .sequenceNumber(sequence)
                .paymentPeriod(contract.getPaymentPeriod())
                .amount(contract.getPrice())
                .status(status)
                .build();
    }
}
