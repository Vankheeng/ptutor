package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.AddressResponse;
import com.ptutor.backend.dto.response.TutorProfileResponse;
import com.ptutor.backend.dto.response.TutorSelfProfileResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;

@Mapper(componentModel = "spring")
public interface TutorProfileMapper {

    @Mapping(target = "tutorId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    TutorProfileResponse toResponse(Tutor tutor);

    @Mapping(target = "tutorId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "gender", source = "user.gender")
    @Mapping(target = "dateOfBirth", source = "user.dateOfBirth")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "address", source = "user")
    TutorSelfProfileResponse toSelfResponse(Tutor tutor);

    default AddressResponse toAddress(User user) {
        District district = user.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new AddressResponse(
                user.getDetailAddress(),
                district == null ? null : district.getId(),
                district == null ? null : district.getName(),
                province == null ? null : province.getId(),
                province == null ? null : province.getName());
    }
}
