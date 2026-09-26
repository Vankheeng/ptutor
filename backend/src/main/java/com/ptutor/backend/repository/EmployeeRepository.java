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

import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.UserStatus;

import jakarta.persistence.LockModeType;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUser_Id(UUID userId);

    @EntityGraph(attributePaths = { "user", "user.district", "user.district.province" })
    @Query("""
            select employee
            from Employee employee
            where employee.role = com.ptutor.backend.entity.enums.EmployeeRole.EMPLOYEE
              and (:status is null or employee.user.status = :status)
              and (:jobFunction is null or employee.jobFunction = :jobFunction)
              and (:keyword = ''
                   or lower(employee.user.email) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(employee.user.phone, '')) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(employee.user.firstName, ''), ' ',
                           coalesce(employee.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
            """)
    Page<Employee> findManagedEmployees(
            @Param("status") UserStatus status,
            @Param("jobFunction") EmployeeJobFunction jobFunction,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = { "user", "user.district", "user.district.province" })
    @Query("select employee from Employee employee where employee.id = :employeeId")
    Optional<Employee> findDetailedById(@Param("employeeId") UUID employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "user.district", "user.district.province" })
    @Query("select employee from Employee employee where employee.id = :employeeId")
    Optional<Employee> findByIdForUpdate(@Param("employeeId") UUID employeeId);
}
