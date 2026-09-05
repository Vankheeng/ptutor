package com.ptutor.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.ComplaintResponse;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Evidence;

@Mapper(componentModel = "spring")
public interface ComplaintMapper {

    @Mapping(target = "userId", source = "complaint.user.id")
    @Mapping(target = "contractId", source = "complaint.contract.id")
    @Mapping(target = "evidences", source = "evidenceResponses")
    ComplaintResponse toResponse(Complaint complaint, List<ComplaintResponse.Evidence> evidenceResponses);

    ComplaintResponse.Evidence toEvidenceResponse(Evidence evidence);
}
