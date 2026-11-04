package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.EmployeeSelfResponse;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeSelfService {

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public EmployeeSelfResponse findMine(UUID userId) {
        Employee employee = employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only a staff account has an employee profile"));
        User user = employee.getUser();
        return new EmployeeSelfResponse(
                employee.getId(), employee.getRole(), employee.getJobFunction(),
                user.getFirstName(), user.getLastName());
    }
}
