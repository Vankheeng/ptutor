package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.CertificateResponse;
import com.ptutor.backend.entity.Certificate;

@Mapper(componentModel = "spring")
public interface CertificateMapper {

    @Mapping(target = "tutorId", source = "tutor.id")
    CertificateResponse toResponse(Certificate certificate);

    @Mapping(target = "tutorId", source = "certificate.tutor.id")
    @Mapping(target = "certificateUrl", source = "certificateUrl")
    CertificateResponse toResponse(Certificate certificate, String certificateUrl);
}
