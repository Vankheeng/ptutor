package com.ptutor.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.auth.dto.UserRole;
import com.ptutor.backend.auth.repository.EmployeeRepository;
import com.ptutor.backend.auth.repository.StudentRepository;
import com.ptutor.backend.auth.repository.TutorRepository;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;

@ExtendWith(MockitoExtension.class)
class RoleResolverTest {

    @Mock StudentRepository studentRepository;
    @Mock TutorRepository tutorRepository;
    @Mock EmployeeRepository employeeRepository;

    private RoleResolver roleResolver;
    private User user;

    @BeforeEach
    void setUp() {
        roleResolver = new RoleResolver(studentRepository, tutorRepository, employeeRepository);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        when(studentRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(tutorRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(employeeRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
    }

    @Test
    void resolvesStudentRole() {
        when(studentRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(Student.builder().build()));

        assertThat(roleResolver.resolve(user)).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void resolvesTutorRole() {
        when(tutorRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(Tutor.builder().build()));

        assertThat(roleResolver.resolve(user)).isEqualTo(UserRole.TUTOR);
    }

    @Test
    void resolvesEmployeeAndAdminRoles() {
        Employee employee = Employee.builder().role(2).build();
        when(employeeRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(employee));
        assertThat(roleResolver.resolve(user)).isEqualTo(UserRole.EMPLOYEE);

        employee.setRole(1);
        assertThat(roleResolver.resolve(user)).isEqualTo(UserRole.ADMIN);
    }
}
