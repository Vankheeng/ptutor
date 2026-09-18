package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.WalletTransactionResponse;
import com.ptutor.backend.entity.WalletTransaction;

@Mapper(componentModel = "spring")
public interface WalletTransactionMapper {

    @Mapping(target = "transactionId", source = "id")
    @Mapping(target = "walletId", source = "wallet.id")
    WalletTransactionResponse toResponse(WalletTransaction transaction);
}
