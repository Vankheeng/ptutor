package com.ptutor.backend.auth.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.auth.dto.RegisterRequest;
import com.ptutor.backend.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth")
    User toUser(RegisterRequest request);
}
