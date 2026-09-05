package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.TutorStudentRequest;

@Mapper(componentModel = "spring")
public interface TutorStudentRequestMapper {

    @Mapping(target = "tutorId", source = "tutor.id")
    @Mapping(target = "studyingRequestId", source = "studyingRequest.id")
    @Mapping(target = "studyingRequestTitle", source = "studyingRequest.title")
    @Mapping(target = "subjectId", source = "studyingRequest.subject.id")
    @Mapping(target = "subjectName", source = "studyingRequest.subject.name")
    @Mapping(target = "gradeId", source = "grade.id")
    @Mapping(target = "gradeName", source = "grade.name")
    TutorStudentRequestResponse toResponse(TutorStudentRequest request);
}
