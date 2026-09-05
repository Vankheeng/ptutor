package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.StudentTutorRequestResponse;
import com.ptutor.backend.entity.StudentTutorRequest;

@Mapper(componentModel = "spring")
public interface StudentTutorRequestMapper {

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentFirstName", source = "student.user.firstName")
    @Mapping(target = "studentLastName", source = "student.user.lastName")
    @Mapping(target = "studentEmail", source = "student.user.email")
    @Mapping(target = "gradeId", source = "grade.id")
    @Mapping(target = "gradeName", source = "grade.name")
    @Mapping(target = "teachingRequestId", source = "teachingRequest.id")
    @Mapping(
            target = "nextStep",
            expression = "java(request.getStatus() == com.ptutor.backend.entity.enums.ApplicationStatus.ACCEPTED ? \"CONTRACT_SIGNING\" : null)")
    StudentTutorRequestResponse toResponse(StudentTutorRequest request);
}
