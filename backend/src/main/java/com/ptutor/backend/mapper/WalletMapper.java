package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.entity.Wallet;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(target = "walletId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "currency", constant = "VND")
    WalletBalanceResponse toBalanceResponse(Wallet wallet);
}
