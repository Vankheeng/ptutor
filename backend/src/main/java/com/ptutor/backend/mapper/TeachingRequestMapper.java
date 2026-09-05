package com.ptutor.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.TeachingRequestResponse;
import com.ptutor.backend.entity.GradeTeachingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.TeachingRequestAvailability;
import com.ptutor.backend.entity.TeachingRequestDistrict;

@Mapper(componentModel = "spring")
public interface TeachingRequestMapper {

    @Mapping(target = "tutorId", source = "request.tutor.id")
    @Mapping(target = "subjectId", source = "request.subject.id")
    @Mapping(target = "subjectName", source = "request.subject.name")
    @Mapping(target = "reviewedBy", source = "request.reviewedBy.id")
    TeachingRequestResponse toResponse(
            TeachingRequest request,
            List<TeachingRequestResponse.Reference> grades,
            List<TeachingRequestResponse.Reference> districts,
            List<TeachingRequestResponse.Availability> availabilities);

    @Mapping(target = "id", source = "grade.id")
    @Mapping(target = "name", source = "grade.name")
    TeachingRequestResponse.Reference toReference(GradeTeachingRequest association);

    @Mapping(target = "id", source = "district.id")
    @Mapping(target = "name", source = "district.name")
    TeachingRequestResponse.Reference toReference(TeachingRequestDistrict association);

    TeachingRequestResponse.Availability toAvailability(TeachingRequestAvailability availability);
}
