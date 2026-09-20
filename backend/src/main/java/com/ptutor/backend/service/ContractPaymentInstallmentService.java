package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
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
                values.add(newInstallment(contract, sequence++, null, cursor));
                cursor = period == PaymentPeriod.WEEKLY ? cursor.plusWeeks(1) : cursor.plusMonths(1);
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
        installment.setDueDate(lesson.getDate());
        installmentRepository.save(installment);
    }

    @Transactional(readOnly = true)
    public boolean canCreateLesson(Contract contract) {
        return contract.getPaymentPeriod() != PaymentPeriod.PER_LESSON
                || installmentRepository.countByContract_IdAndLessonIsNotNull(contract.getId())
                        < contract.getTotalLession();
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
