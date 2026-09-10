package com.ptutor.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WalletMapper;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public WalletBalanceResponse getCurrentBalance() {
        return walletRepository.findByUser_Id(currentUserProvider.getCurrentUserId())
                .map(walletMapper::toBalanceResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
    }
}
