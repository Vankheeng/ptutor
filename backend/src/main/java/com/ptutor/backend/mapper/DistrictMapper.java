package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.DistrictResponse;
import com.ptutor.backend.entity.District;

@Mapper(componentModel = "spring")
public interface DistrictMapper {

    @Mapping(target = "provinceId", source = "province.id")
    @Mapping(target = "provinceName", source = "province.name")
    DistrictResponse toResponse(District district);
}
