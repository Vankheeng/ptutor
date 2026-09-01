package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByUser_Id(UUID userId);

    @Query("""
            select student
            from Student student
            join fetch student.user user
            left join fetch user.district district
            left join fetch district.province
            where user.id = :userId
            """)
    Optional<Student> findProfileByUserId(@Param("userId") UUID userId);
}
