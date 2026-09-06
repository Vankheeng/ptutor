package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.entity.Contract;

@Mapper(componentModel = "spring")
public interface ContractMapper {

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentFirstName", source = "student.user.firstName")
    @Mapping(target = "studentLastName", source = "student.user.lastName")
    @Mapping(target = "studentEmail", source = "student.user.email")
    @Mapping(target = "tutorId", source = "tutor.id")
    @Mapping(target = "tutorFirstName", source = "tutor.user.firstName")
    @Mapping(target = "tutorLastName", source = "tutor.user.lastName")
    @Mapping(target = "tutorEmail", source = "tutor.user.email")
    @Mapping(target = "subjectId", source = "subject.id")
    @Mapping(target = "subjectName", source = "subject.name")
    @Mapping(target = "gradeId", source = "grade.id")
    @Mapping(target = "gradeName", source = "grade.name")
    @Mapping(target = "totalLessons", source = "totalLession")
    @Mapping(target = "createdByUserId", source = "createdBy.id")
    @Mapping(target = "signedByUserId", source = "signedBy.id")
    @Mapping(target = "tutorStudentRequestId", source = "tutorStudentRequest.id")
    @Mapping(target = "studentTutorRequestId", source = "studentTutorRequest.id")
    @Mapping(target = "renewedFromContractId", source = "renewedFromContract.id")
    ContractResponse toResponse(Contract contract);
}
