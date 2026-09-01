package com.ptutor.backend.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.ptutor.backend.dto.request.UpdateStudentProfileRequest;
import com.ptutor.backend.dto.response.AddressResponse;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;

@Mapper(componentModel = "spring")
public interface StudentProfileMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "studentId", source = "id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "gender", source = "user.gender")
    @Mapping(target = "dateOfBirth", source = "user.dateOfBirth")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "address", source = "user")
    StudentProfileResponse toResponse(Student student);

    @BeanMapping(
            ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "detailAddress", source = "detailAddress")
    void updateUser(UpdateStudentProfileRequest request, @MappingTarget User user);

    @BeanMapping(
            ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "introduction", source = "introduction")
    @Mapping(target = "learningStyle", source = "learningStyle")
    @Mapping(target = "personalityTags", source = "personalityTags")
    @Mapping(target = "goalsDescription", source = "goalsDescription")
    @Mapping(target = "currentLevel", source = "currentLevel")
    @Mapping(target = "weakPoints", source = "weakPoints")
    void updateStudent(UpdateStudentProfileRequest request, @MappingTarget Student student);

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
