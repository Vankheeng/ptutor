package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.mapper.BankAccountMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public BankAccountResponse getCurrentBankAccount() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return bankAccountRepository.findByUser_Id(userId)
                .map(bankAccountMapper::toResponse)
                .orElse(null);
    }

}
