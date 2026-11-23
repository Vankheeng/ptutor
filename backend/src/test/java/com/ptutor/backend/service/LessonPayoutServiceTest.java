package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class LessonPayoutServiceTest {
    @Mock ContractRepository contracts;
    @Mock ContractPaymentInstallmentRepository installments;
    @Mock WalletTransactionRepository transactions;
    @Mock RefundRequestLessonRepository disputedLessons;
    @Mock ContractPaymentInstallmentService installmentService;
    @Mock WalletService wallets;
    @Mock JdbcTemplate jdbc;

    private LessonPayoutService service;
    private Lesson lesson;
    private UUID tutorId;

    @BeforeEach
    void setUp() {
        service = new LessonPayoutService(contracts, installments, transactions, disputedLessons,
                installmentService, wallets, jdbc);
        tutorId = UUID.randomUUID();
        User tutorUser = User.builder().build();
        tutorUser.setId(tutorId);
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        Contract contract = Contract.builder().tutor(tutor).totalLession(4).build();
        contract.setId(UUID.randomUUID());
        lesson = Lesson.builder().contract(contract).status(LessonStatus.COMPLETED).build();
        lesson.setId(UUID.randomUUID());
    }

    @Test
    void creditsOneLessonShareAndReferencesLesson() {
        stubContractAndInstallment();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(lesson.getContract().getId())))
                .thenReturn(new BigDecimal("1000000"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(lesson.getContract().getId()),
                eq(lesson.getContract().getId()), eq(lesson.getContract().getId())))
                .thenReturn(new BigDecimal("250000"));

        service.release(lesson);

        ArgumentCaptor<WalletTransactionCommand> command = ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(wallets).credit(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(tutorId);
        assertThat(command.getValue().amount()).isEqualByComparingTo("250000");
        assertThat(command.getValue().purpose()).isEqualTo(WalletTransactionPurpose.TUTOR_EARNING);
        assertThat(command.getValue().referenceType()).isEqualTo(ReferenceType.LESSON);
        assertThat(command.getValue().referenceId()).isEqualTo(lesson.getId());
    }

    @Test
    void repeatedPayoutDoesNotCreditTwice() {
        when(contracts.findByIdForSettlement(lesson.getContract().getId()))
                .thenReturn(Optional.of(lesson.getContract()));
        when(transactions.findByWallet_User_IdAndIdempotencyKey(tutorId, "lesson-payout-" + lesson.getId()))
                .thenReturn(Optional.of(WalletTransaction.builder().build()));

        service.release(lesson);

        verify(wallets, never()).credit(any());
    }

    @Test
    void disputedLessonCannotBePaidWhileRefundIsOpen() {
        when(contracts.findByIdForSettlement(lesson.getContract().getId()))
                .thenReturn(Optional.of(lesson.getContract()));
        when(disputedLessons.existsByLesson_IdAndRefundRequest_StatusIn(eq(lesson.getId()),
                eq(List.of(RefundRequestStatus.PENDING, RefundRequestStatus.IN_REVIEW)))).thenReturn(true);

        assertThatThrownBy(() -> service.release(lesson))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("LESSON_COMPLETION_ON_HOLD"));
        verify(wallets, never()).credit(any());
    }

    @Test
    void insufficientHeldTuitionPreventsPayout() {
        stubContractAndInstallment();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(lesson.getContract().getId())))
                .thenReturn(new BigDecimal("200000"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(lesson.getContract().getId()),
                eq(lesson.getContract().getId()), eq(lesson.getContract().getId())))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.release(lesson))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_HELD_TUITION");
                });
        verify(wallets, never()).credit(any());
    }

    private void stubContractAndInstallment() {
        Contract contract = lesson.getContract();
        when(contracts.findByIdForSettlement(contract.getId())).thenReturn(Optional.of(contract));
        ContractPaymentInstallment installment = ContractPaymentInstallment.builder()
                .amount(new BigDecimal("1000000")).status(PaymentInstallmentStatus.PAID).build();
        when(installments.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of(installment));
    }
}
