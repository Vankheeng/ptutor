package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            values.add(newInstallment(contract, 1, null, contract.getStartDate(), contract.getPrice()));
        } else if (period == PaymentPeriod.PER_LESSON) {
            for (int sequence = 1; sequence <= contract.getTotalLession(); sequence++) {
                values.add(newInstallment(contract, sequence, null, null, contract.getPrice()));
            }
        } else {
            LocalDate cursor = contract.getStartDate();
            int sequence = 1;
            while (!cursor.isAfter(contract.getEndDate())) {
                LocalDate nextCursor = nextCycleStart(cursor, period);
                LocalDate cycleEndExclusive = !nextCursor.isBefore(contract.getEndDate())
                        ? contract.getEndDate().plusDays(1)
                        : nextCursor;
                LocalDate firstLessonDate = firstScheduledDate(contract, cursor, cycleEndExclusive)
                        .orElse(cursor);
                BigDecimal amount = calculateCycleAmount(contract, cursor, cycleEndExclusive);
                values.add(newInstallment(contract, sequence++, null, paymentDueDate(firstLessonDate), amount));
                if (!nextCursor.isBefore(contract.getEndDate())) {
                    break;
                }
                cursor = nextCursor;
            }
        }
        return installmentRepository.saveAll(values);
    }

    @Transactional
    public void assignNextInstallment(Contract contract, Lesson lesson) {
        List<ContractPaymentInstallment> installments = ensureForActiveContract(contract);
        ContractPaymentInstallment installment;
        if (contract.getPaymentPeriod() == PaymentPeriod.PER_LESSON) {
            installment = installmentRepository
                    .findFirstByContract_IdAndStatusAndLessonIsNullOrderBySequenceNumberAsc(
                            contract.getId(), PaymentInstallmentStatus.PENDING)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CONTRACT_LESSON_LIMIT_REACHED",
                            "The contract already has its maximum number of payable lessons"));
            installment.setLesson(lesson);
            installment.setDueDate(paymentDueDate(lesson.getDate()));
            installmentRepository.save(installment);
        } else {
            installment = findInstallmentForLessonDate(contract, lesson.getDate(), installments);
            if (installment == null) {
                throw new ApiException(HttpStatus.CONFLICT, "LESSON_OUTSIDE_PAYMENT_PERIOD",
                        "The lesson date is outside the contract payment periods");
            }
        }
        lesson.setPaymentInstallment(installment);
    }

    @Transactional
    public void updatePendingLessonInstallmentDueDate(Lesson lesson) {
        if (lesson.getContract().getPaymentPeriod() != PaymentPeriod.PER_LESSON) return;
        installmentRepository.findByLesson_Id(lesson.getId())
                .filter(installment -> installment.getStatus() == PaymentInstallmentStatus.PENDING)
                .ifPresent(installment -> {
                    installment.setDueDate(paymentDueDate(lesson.getDate()));
                    installmentRepository.save(installment);
                });
    }

    @Transactional
    public void releasePendingLessonInstallment(Lesson lesson) {
        if (lesson.getContract().getPaymentPeriod() != PaymentPeriod.PER_LESSON) return;
        installmentRepository.findByLesson_Id(lesson.getId())
                .filter(installment -> installment.getStatus() == PaymentInstallmentStatus.PENDING)
                .ifPresent(installment -> {
                    installment.setLesson(null);
                    installment.setDueDate(null);
                    installmentRepository.save(installment);
                });
    }

    @Transactional(readOnly = true)
    public Optional<ContractPaymentInstallment> findByLesson(Lesson lesson) {
        if (lesson == null || lesson.getId() == null) {
            return Optional.empty();
        }
        return installmentRepository.findByLesson_Id(lesson.getId());
    }

    @Transactional
    public Optional<ContractPaymentInstallment> findForLesson(Lesson lesson) {
        if (lesson == null || lesson.getContract() == null || lesson.getDate() == null) {
            return Optional.empty();
        }
        if (lesson.getPaymentInstallment() != null) {
            return Optional.of(lesson.getPaymentInstallment());
        }
        Contract contract = lesson.getContract();
        return findForLesson(lesson, ensureForActiveContract(contract));
    }

    public Optional<ContractPaymentInstallment> findForLesson(
            Lesson lesson, List<ContractPaymentInstallment> installments) {
        if (lesson == null || lesson.getContract() == null || lesson.getDate() == null) {
            return Optional.empty();
        }
        Contract contract = lesson.getContract();
        if (installments.size() == 1 && installments.get(0).getLesson() == null) {
            return Optional.of(installments.get(0));
        }
        if (contract.getPaymentPeriod() == PaymentPeriod.PER_LESSON) {
            return findByLesson(lesson);
        }
        if (contract.getPaymentPeriod() == PaymentPeriod.MONTHLY
                && lesson.getPaymentInstallment() == null) {
            // Legacy monthly lessons are ambiguous after a reschedule. Do not
            // silently charge/read them from the new calendar month.
            return Optional.empty();
        }
        return Optional.ofNullable(findInstallmentForLessonDate(contract, lesson.getDate(), installments));
    }

    @Transactional
    public void requirePaidForLesson(Lesson lesson) {
        Contract contract = lesson.getContract();
        ContractPaymentInstallment installment = findForLesson(lesson).orElse(null);
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
        while (!cursor.isAfter(contract.getEndDate())) {
            LocalDate nextCursor = nextCycleStart(cursor, contract.getPaymentPeriod());
            LocalDate cycleEndExclusive = !nextCursor.isBefore(contract.getEndDate())
                    ? contract.getEndDate().plusDays(1)
                    : nextCursor;
            if (!lessonDate.isBefore(cursor) && lessonDate.isBefore(cycleEndExclusive)) {
                final int matchingSequence = sequence;
                return installments.stream()
                        .filter(installment -> installment.getSequenceNumber() == matchingSequence)
                        .findFirst()
                        .orElse(null);
            }
            if (!nextCursor.isBefore(contract.getEndDate())) {
                break;
            }
            cursor = nextCursor;
            sequence++;
        }
        return null;
    }

    private BigDecimal calculateCycleAmount(
            Contract contract, LocalDate cycleStart, LocalDate cycleEndExclusive) {
        List<ContractSchedule> schedules = contract.getSchedules();
        if (schedules == null || schedules.isEmpty()) {
            // Legacy contracts only have a free-form preferred_schedule and cannot be
            // reconstructed reliably. Keep their historical price semantics.
            return contract.getPrice();
        }

        long lessonsInCycle = 0;
        for (LocalDate date = cycleStart; date.isBefore(cycleEndExclusive); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            lessonsInCycle += schedules.stream()
                    .filter(schedule -> schedule.getDayOfWeek() != null
                            && schedule.getDayOfWeek() == dayOfWeek)
                    .count();
        }
        return contract.getPrice().multiply(BigDecimal.valueOf(lessonsInCycle));
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
            Contract contract, int sequence, Lesson lesson, LocalDate dueDate, BigDecimal amount) {
        return ContractPaymentInstallment.builder()
                .contract(contract)
                .lesson(lesson)
                .sequenceNumber(sequence)
                .paymentPeriod(contract.getPaymentPeriod())
                .amount(amount)
                .dueDate(dueDate)
                .status(PaymentInstallmentStatus.PENDING)
                .build();
    }
}
