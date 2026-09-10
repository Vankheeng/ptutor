package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.entity.BankAccount;

@Mapper(componentModel = "spring")
public interface BankAccountMapper {

    @Mapping(target = "bankAccountId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "maskedAccountNumber", source = "accountNumberLastFour", qualifiedByName = "maskAccountNumber")
    BankAccountResponse toResponse(BankAccount bankAccount);

    @Named("maskAccountNumber")
    default String maskAccountNumber(String lastFour) {
        return "****" + lastFour;
    }
}
