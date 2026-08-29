package com.ptutor.backend.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ptutor.backend.auth.dto.UserRole;
import com.ptutor.backend.auth.exception.ApiException;
import com.ptutor.backend.auth.repository.EmployeeRepository;
import com.ptutor.backend.auth.repository.StudentRepository;
import com.ptutor.backend.auth.repository.TutorRepository;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleResolver {

    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final EmployeeRepository employeeRepository;

    public UserRole resolve(User user) {
        boolean student = studentRepository.findByUser_Id(user.getId()).isPresent();
        boolean tutor = tutorRepository.findByUser_Id(user.getId()).isPresent();
        Employee employee = employeeRepository.findByUser_Id(user.getId()).orElse(null);

        int profileCount = (student ? 1 : 0) + (tutor ? 1 : 0) + (employee != null ? 1 : 0);
        if (profileCount != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_USER_PROFILE",
                    "User profile is missing or inconsistent");
        }
        if (student) {
            return UserRole.STUDENT;
        }
        if (tutor) {
            return UserRole.TUTOR;
        }
        if (employee.getRole() == EmployeeRole.ADMIN) {
            return UserRole.ADMIN;
        }
        if (employee.getRole() == EmployeeRole.EMPLOYEE) {
            return UserRole.EMPLOYEE;
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_EMPLOYEE_ROLE",
                "Employee role is not supported");
    }
}
