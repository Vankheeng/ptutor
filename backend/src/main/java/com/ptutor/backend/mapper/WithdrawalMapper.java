package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.entity.WithdrawalRequest;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {

    @Mapping(target = "withdrawalId", source = "id")
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "reviewedByUserId", source = "reviewedByUser.id")
    @Mapping(target = "maskedAccountNumber", source = "accountNumberLastFour", qualifiedByName = "mask")
    WithdrawalResponse toResponse(WithdrawalRequest value);

    @Named("mask")
    default String mask(String lastFour) {
        return "****" + lastFour;
    }
}
