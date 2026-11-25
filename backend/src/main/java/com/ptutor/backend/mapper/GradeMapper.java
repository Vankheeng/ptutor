package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;

import com.ptutor.backend.dto.response.GradeResponse;
import com.ptutor.backend.entity.Grade;

@Mapper(componentModel = "spring")
public interface GradeMapper {

    GradeResponse toResponse(Grade grade);
}
