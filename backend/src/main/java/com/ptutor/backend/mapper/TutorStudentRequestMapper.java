package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.TutorStudentRequest;

@Mapper(componentModel = "spring")
public interface TutorStudentRequestMapper {

    @Mapping(target = "tutorId", source = "tutor.id")
    @Mapping(target = "tutorFirstName", source = "tutor.user.firstName")
    @Mapping(target = "tutorLastName", source = "tutor.user.lastName")
    @Mapping(target = "tutorEmail", source = "tutor.user.email")
    @Mapping(target = "gradeId", source = "grade.id")
    @Mapping(target = "gradeName", source = "grade.name")
    @Mapping(target = "studyingRequestId", source = "studyingRequest.id")
    @Mapping(
            target = "nextStep",
            expression = "java(request.getStatus() == com.ptutor.backend.entity.enums.ApplicationStatus.ACCEPTED ? \"CONTRACT_SIGNING\" : null)")
    TutorStudentRequestResponse toResponse(TutorStudentRequest request);
}
