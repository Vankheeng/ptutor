package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
class EmployeeSelfServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @InjectMocks EmployeeSelfService service;

    @Test
    void returnsTheSignedInEmployeesJobFunction() {
        UUID userId = UUID.randomUUID();
        Employee employee = Employee.builder()
                .user(User.builder().firstName("Lan").lastName("Nguyen").build())
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.ACCOUNTANT)
                .build();
        when(employeeRepository.findByUser_Id(userId)).thenReturn(Optional.of(employee));

        var response = service.findMine(userId);

        assertThat(response.role()).isEqualTo(EmployeeRole.EMPLOYEE);
        assertThat(response.jobFunction()).isEqualTo(EmployeeJobFunction.ACCOUNTANT);
        assertThat(response.firstName()).isEqualTo("Lan");
    }

    @Test
    void refusesAccountsWithoutEmployeeProfile() {
        UUID userId = UUID.randomUUID();
        when(employeeRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMine(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
