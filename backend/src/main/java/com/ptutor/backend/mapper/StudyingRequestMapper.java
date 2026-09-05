package com.ptutor.backend.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.ptutor.backend.dto.request.StudyingRequestUpdateRequest;
import com.ptutor.backend.dto.response.StudyingRequestResponse;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.StudyingRequestAvailability;

@Mapper(componentModel = "spring")
public interface StudyingRequestMapper {

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "studentId", source = "request.student.id")
    @Mapping(target = "subjectId", source = "request.subject.id")
    @Mapping(target = "subjectName", source = "request.subject.name")
    @Mapping(target = "gradeId", source = "request.grade.id")
    @Mapping(target = "gradeName", source = "request.grade.name")
    @Mapping(target = "districtId", source = "request.district.id")
    @Mapping(target = "districtName", source = "request.district.name")
    @Mapping(target = "availabilities", source = "availabilityResponses")
    StudyingRequestResponse toResponse(
            StudyingRequest request,
            List<StudyingRequestResponse.Availability> availabilityResponses);

    StudyingRequestResponse.Availability toAvailability(StudyingRequestAvailability availability);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "grade", ignore = true)
    @Mapping(target = "district", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "availabilities", ignore = true)
    void updateEntity(StudyingRequestUpdateRequest source, @MappingTarget StudyingRequest target);
}
