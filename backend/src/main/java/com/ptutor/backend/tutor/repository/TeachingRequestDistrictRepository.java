package com.ptutor.backend.tutor.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.TeachingRequestDistrict;

public interface TeachingRequestDistrictRepository extends JpaRepository<TeachingRequestDistrict, UUID> {

    List<TeachingRequestDistrict> findAllByTeachingRequest_Id(UUID requestId);
}
