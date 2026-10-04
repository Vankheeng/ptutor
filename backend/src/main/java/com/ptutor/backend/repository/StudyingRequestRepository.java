package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

public interface StudyingRequestRepository extends JpaRepository<StudyingRequest, UUID> {

    @EntityGraph(attributePaths = {"subject", "grade", "district", "availabilities"})
    @Query(value = """
            select distinct request from StudyingRequest request
            where (:status is null or request.status = :status)
              and (:subjectId is null or request.subject.id = :subjectId)
              and (:gradeId is null or request.grade.id = :gradeId)
              and (:districtId is null or request.district.id = :districtId)
              and (:learningMode is null or request.learningMode = :learningMode)
            """,
            countQuery = """
            select count(request) from StudyingRequest request
            where (:status is null or request.status = :status)
              and (:subjectId is null or request.subject.id = :subjectId)
              and (:gradeId is null or request.grade.id = :gradeId)
              and (:districtId is null or request.district.id = :districtId)
              and (:learningMode is null or request.learningMode = :learningMode)
            """)
    Page<StudyingRequest> searchRequests(
            @Param("status") RequestStatus status,
            @Param("subjectId") UUID subjectId,
            @Param("gradeId") UUID gradeId,
            @Param("districtId") UUID districtId,
            @Param("learningMode") LearningMode learningMode,
            Pageable pageable);

    @EntityGraph(attributePaths = {"subject", "grade", "district", "availabilities"})
    @Query("""
            select request from StudyingRequest request
            where request.id = :id and (:status is null or request.status = :status)
            """)
    Optional<StudyingRequest> findSearchableById(
            @Param("id") UUID id, @Param("status") RequestStatus status);

    @EntityGraph(attributePaths = {"student", "subject", "grade", "district"})
    Page<StudyingRequest> findAllByStudent_IdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "subject", "grade", "district"})
    Page<StudyingRequest> findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
            UUID studentId, com.ptutor.backend.entity.enums.RequestStatus status, Pageable pageable);

    Optional<StudyingRequest> findByIdAndStudent_Id(UUID id, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from StudyingRequest request
            where request.id = :requestId
              and request.student.id = :studentId
            """)
    Optional<StudyingRequest> findByIdAndStudentIdForUpdate(
            @Param("requestId") UUID requestId,
            @Param("studentId") UUID studentId);

    @Query("""
            select distinct request
            from StudyingRequest request
            join fetch request.student student
            join fetch request.subject subject
            join fetch request.grade grade
            left join fetch request.district district
            left join fetch request.availabilities availability
            where request.id = :requestId
              and student.id = :studentId
            """)
    Optional<StudyingRequest> findDetailedByIdAndStudentId(
            @Param("requestId") UUID requestId,
            @Param("studentId") UUID studentId);
}
