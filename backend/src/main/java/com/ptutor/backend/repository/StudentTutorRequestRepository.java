package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface StudentTutorRequestRepository extends JpaRepository<StudentTutorRequest, UUID> {

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    @Query(value = """
            select application
            from StudentTutorRequest application
            join application.teachingRequest teachingRequest
            left join teachingRequest.subject subject
            join teachingRequest.tutor tutor
            join tutor.user tutorUser
            join application.grade grade
            where application.student.id = :studentId
              and (:status is null or application.status = :status)
              and (
                   :keyword = ''
                   or lower(coalesce(teachingRequest.title, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(teachingRequest.note, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(subject.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(teachingRequest.customSubjectName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(tutorUser.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(tutorUser.firstName, ''), ' ', coalesce(tutorUser.lastName, '')))
                      like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(grade.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(application.preferredSchedule, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(application.message, '')) like lower(concat('%', :keyword, '%'))
              )
            order by application.createdAt desc
            """, countQuery = """
            select count(application)
            from StudentTutorRequest application
            join application.teachingRequest teachingRequest
            left join teachingRequest.subject subject
            join teachingRequest.tutor tutor
            join tutor.user tutorUser
            join application.grade grade
            where application.student.id = :studentId
              and (:status is null or application.status = :status)
              and (
                   :keyword = ''
                   or lower(coalesce(teachingRequest.title, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(teachingRequest.note, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(subject.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(teachingRequest.customSubjectName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(tutorUser.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(tutorUser.firstName, ''), ' ', coalesce(tutorUser.lastName, '')))
                      like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(grade.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(application.preferredSchedule, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(application.message, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<StudentTutorRequest> findAllForStudent(
            @Param("studentId") UUID studentId,
            @Param("status") ApplicationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Page<StudentTutorRequest> findAllByStudent_IdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Page<StudentTutorRequest> findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
            UUID studentId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Optional<StudentTutorRequest> findByIdAndStudent_Id(UUID id, UUID studentId);

    boolean existsByStudent_IdAndTeachingRequest_IdAndStatus(
            UUID studentId, UUID teachingRequestId, ApplicationStatus status);

    List<StudentTutorRequest> findAllByTeachingRequest_IdOrderByCreatedAtDesc(UUID teachingRequestId);

    List<StudentTutorRequest> findAllByTeachingRequest_IdAndStatusOrderByCreatedAtDesc(
            UUID teachingRequestId, ApplicationStatus status);

    Optional<StudentTutorRequest> findByIdAndTeachingRequest_Id(UUID id, UUID teachingRequestId);

    long countByTeachingRequest_IdAndStatus(UUID teachingRequestId, ApplicationStatus status);

    @Query("""
            select application.teachingRequest.id as teachingRequestId,
                   count(application.id) as studentRequestCount,
                   sum(case when application.status = :pendingStatus then 1 else 0 end) as pendingStudentRequestCount
            from StudentTutorRequest application
            where application.teachingRequest.id in :teachingRequestIds
            group by application.teachingRequest.id
            """)
    List<TeachingRequestStudentRequestCount> countByTeachingRequestIds(
            @Param("teachingRequestIds") List<UUID> teachingRequestIds,
            @Param("pendingStatus") ApplicationStatus pendingStatus);
}
