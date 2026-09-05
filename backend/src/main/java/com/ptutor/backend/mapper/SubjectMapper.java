package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;

import com.ptutor.backend.dto.response.SubjectResponse;
import com.ptutor.backend.entity.Subject;

@Mapper(componentModel = "spring")
public interface SubjectMapper {

    SubjectResponse toResponse(Subject subject);
}
