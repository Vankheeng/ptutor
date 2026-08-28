package com.ptutor.backend.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.auth.repository.EmployeeRepository;
import com.ptutor.backend.auth.repository.UserRepository;
import com.ptutor.backend.entity.User;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AdminBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        bootstrapService = new AdminBootstrapService(userRepository, employeeRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrapService, "adminEmail", "admin@ptutor.local");
        ReflectionTestUtils.setField(bootstrapService, "adminPassword", "admin");
    }

    @Test
    void createsAdminWhenItDoesNotExist() {
        User admin = User.builder().email("admin@ptutor.local").build();
        when(userRepository.findAnyByEmailIgnoreCase("admin@ptutor.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin")).thenReturn("hashed-admin-password");
        when(userRepository.save(any(User.class))).thenReturn(admin);

        bootstrapService.run();

        verify(userRepository).save(any(User.class));
        verify(employeeRepository).save(any());
    }

    @Test
    void doesNotCreateDuplicateAdmin() {
        when(userRepository.findAnyByEmailIgnoreCase("admin@ptutor.local"))
                .thenReturn(Optional.of(User.builder().build()));

        bootstrapService.run();

        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }
}
