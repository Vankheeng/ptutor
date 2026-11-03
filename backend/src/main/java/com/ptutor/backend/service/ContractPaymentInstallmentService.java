package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.ContractSchedule;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContractPaymentInstallmentService {

    private final ContractPaymentInstallmentRepository installmentRepository;
    private final ContractTimeProvider contractTimeProvider;

    @Transactional
    public List<ContractPaymentInstallment> ensureForActiveContract(Contract contract) {
        List<ContractPaymentInstallment> existing = installmentRepository
                .findAllByContract_IdOrderBySequenceNumberAsc(contract.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            return List.of();
        }
        if (contract.getPrice() == null || contract.getPrice().signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CONTRACT_PAYMENT_AMOUNT",
                    "A contract price must be greater than zero before payments can be created");
        }

        List<ContractPaymentInstallment> values = new ArrayList<>();
        PaymentPeriod period = contract.getPaymentPeriod();
        if (period == PaymentPeriod.PACKAGE) {
            values.add(newInstallment(contract, 1, null, contract.getStartDate()));
        } else if (period == PaymentPeriod.PER_LESSON) {
            for (int sequence = 1; sequence <= contract.getTotalLession(); sequence++) {
                values.add(newInstallment(contract, sequence, null, null));
            }
        } else {
            LocalDate cursor = contract.getStartDate();
            int sequence = 1;
            while (cursor.isBefore(contract.getEndDate())) {
                LocalDate nextCursor = nextCycleStart(cursor, period);
                LocalDate cycleEndExclusive = nextCursor.isAfter(contract.getEndDate())
                        ? contract.getEndDate().plusDays(1)
                        : nextCursor;
                LocalDate firstLessonDate = firstScheduledDate(contract, cursor, cycleEndExclusive)
                        .orElse(cursor);
                values.add(newInstallment(contract, sequence++, null, paymentDueDate(firstLessonDate)));
                cursor = nextCursor;
            }
        }
        return installmentRepository.saveAll(values);
    }

    @Transactional
    public void assignNextInstallment(Contract contract, Lesson lesson) {
        if (contract.getPaymentPeriod() != PaymentPeriod.PER_LESSON) {
            return;
        }
        ensureForActiveContract(contract);
        ContractPaymentInstallment installment = installmentRepository
                .findFirstByContract_IdAndStatusAndLessonIsNullOrderBySequenceNumberAsc(
                        contract.getId(), PaymentInstallmentStatus.PENDING)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CONTRACT_LESSON_LIMIT_REACHED",
                        "The contract already has its maximum number of payable lessons"));
        installment.setLesson(lesson);
        installment.setDueDate(paymentDueDate(lesson.getDate()));
        installmentRepository.save(installment);
    }

    @Transactional
    public void updatePendingLessonInstallmentDueDate(Lesson lesson) {
        if (lesson.getContract().getPaymentPeriod() != PaymentPeriod.PER_LESSON) {
            return;
        }
        installmentRepository.findByLesson_Id(lesson.getId())
                .filter(installment -> installment.getStatus() == PaymentInstallmentStatus.PENDING)
                .ifPresent(installment -> {
                    installment.setDueDate(paymentDueDate(lesson.getDate()));
                    installmentRepository.save(installment);
                });
    }

    @Transactional
    public void releasePendingLessonInstallment(Lesson lesson) {
        if (lesson.getContract().getPaymentPeriod() != PaymentPeriod.PER_LESSON) {
            return;
        }
        installmentRepository.findByLesson_Id(lesson.getId())
                .filter(installment -> installment.getStatus() == PaymentInstallmentStatus.PENDING)
                .ifPresent(installment -> {
                    installment.setLesson(null);
                    installment.setDueDate(null);
                    installmentRepository.save(installment);
                });
    }

    @Transactional
    public void requirePaidForLesson(Lesson lesson) {
        Contract contract = lesson.getContract();
        PaymentPeriod period = contract.getPaymentPeriod();
        if (period == PaymentPeriod.PACKAGE) {
            return;
        }

        List<ContractPaymentInstallment> installments = ensureForActiveContract(contract);
        ContractPaymentInstallment installment = period == PaymentPeriod.PER_LESSON
                ? installmentRepository.findByLesson_Id(lesson.getId()).orElse(null)
                : findInstallmentForLessonDate(contract, lesson.getDate(), installments);
        if (installment == null || installment.getStatus() != PaymentInstallmentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "INSTALLMENT_PAYMENT_REQUIRED",
                    "The related tuition installment must be paid before this lesson can be marked as taught");
        }
    }

    @Transactional(readOnly = true)
    public boolean canCreateLesson(Contract contract) {
        return contract.getPaymentPeriod() != PaymentPeriod.PER_LESSON
                || installmentRepository.countByContract_IdAndLessonIsNotNull(contract.getId())
                        < contract.getTotalLession();
    }

    private ContractPaymentInstallment findInstallmentForLessonDate(
            Contract contract, LocalDate lessonDate, List<ContractPaymentInstallment> installments) {
        LocalDate cursor = contract.getStartDate();
        int sequence = 1;
        while (cursor.isBefore(contract.getEndDate())) {
            LocalDate nextCursor = nextCycleStart(cursor, contract.getPaymentPeriod());
            if (!lessonDate.isBefore(cursor) && lessonDate.isBefore(nextCursor)) {
                final int matchingSequence = sequence;
                return installments.stream()
                        .filter(installment -> installment.getSequenceNumber() == matchingSequence)
                        .findFirst()
                        .orElse(null);
            }
            cursor = nextCursor;
            sequence++;
        }
        return null;
    }

    private java.util.Optional<LocalDate> firstScheduledDate(
            Contract contract, LocalDate cycleStart, LocalDate cycleEndExclusive) {
        List<ContractSchedule> schedules = contract.getSchedules();
        if (schedules == null || schedules.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (LocalDate date = cycleStart; date.isBefore(cycleEndExclusive); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            boolean scheduled = schedules.stream().anyMatch(schedule -> schedule.getDayOfWeek() != null
                    && schedule.getDayOfWeek() == day.getValue());
            if (scheduled) {
                return java.util.Optional.of(date);
            }
        }
        return java.util.Optional.empty();
    }

    private LocalDate nextCycleStart(LocalDate date, PaymentPeriod period) {
        return period == PaymentPeriod.WEEKLY ? date.plusWeeks(1) : date.plusMonths(1);
    }

    private LocalDate paymentDueDate(LocalDate lessonOrCycleDate) {
        LocalDate today = contractTimeProvider.today();
        LocalDate deadline = lessonOrCycleDate.minusDays(3);
        return deadline.isBefore(today) ? today : deadline;
    }

    private ContractPaymentInstallment newInstallment(
            Contract contract, int sequence, Lesson lesson, LocalDate dueDate) {
        return ContractPaymentInstallment.builder()
                .contract(contract)
                .lesson(lesson)
                .sequenceNumber(sequence)
                .paymentPeriod(contract.getPaymentPeriod())
                .amount(contract.getPrice())
                .dueDate(dueDate)
                .status(PaymentInstallmentStatus.PENDING)
                .build();
    }
}
