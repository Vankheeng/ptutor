package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.District;

public interface DistrictRepository extends JpaRepository<District, UUID> {

    @Query("select d from District d join fetch d.province p order by p.name asc, d.name asc")
    List<District> findAllOrdered();

    @Query("select d from District d join fetch d.province p where p.id = :provinceId order by d.name asc")
    List<District> findAllByProvinceIdOrdered(@Param("provinceId") UUID provinceId);
}
