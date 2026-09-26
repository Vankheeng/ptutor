package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.enums.ComplaintStatus;

import jakarta.persistence.LockModeType;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    Page<Complaint> findAllByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Complaint> findAllByUser_IdAndStatusOrderByCreatedAtDesc(
            UUID userId, ComplaintStatus status, Pageable pageable);

    Optional<Complaint> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByContract_IdAndStatusIn(UUID contractId, Collection<ComplaintStatus> statuses);

    @EntityGraph(attributePaths = {
            "user", "contract", "contract.student", "contract.student.user", "contract.tutor",
            "contract.tutor.user", "employee", "employee.user"
    })
    @Query(value = """
            select complaint
            from Complaint complaint
            where (:status is null or complaint.status = :status)
              and (:contractId is null or complaint.contract.id = :contractId)
              and (:keyword = ''
                   or lower(complaint.title) like lower(concat('%', :keyword, '%'))
                   or lower(complaint.user.email) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(complaint.user.firstName, ''), ' ',
                           coalesce(complaint.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
            """,
            countQuery = """
                    select count(complaint)
                    from Complaint complaint
                    where (:status is null or complaint.status = :status)
                      and (:contractId is null or complaint.contract.id = :contractId)
                      and (:keyword = ''
                           or lower(complaint.title) like lower(concat('%', :keyword, '%'))
                           or lower(complaint.user.email) like lower(concat('%', :keyword, '%'))
                           or lower(concat(coalesce(complaint.user.firstName, ''), ' ',
                                   coalesce(complaint.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
                    """)
    Page<Complaint> findAllForReview(
            @Param("status") ComplaintStatus status,
            @Param("contractId") UUID contractId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "contract", "contract.student", "contract.student.user", "contract.tutor",
            "contract.tutor.user", "contract.subject", "contract.grade", "employee", "employee.user"
    })
    @Query("select complaint from Complaint complaint where complaint.id = :id")
    Optional<Complaint> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "contract", "employee", "employee.user" })
    @Query("select complaint from Complaint complaint where complaint.id = :id")
    Optional<Complaint> findByIdForUpdate(@Param("id") UUID id);
}
