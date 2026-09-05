package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.entity.Lesson;

@Mapper(componentModel = "spring")
public interface LessonMapper {

    @Mapping(target = "contractId", source = "contract.id")
    LessonResponse toResponse(Lesson lesson);
}
