package com.ptutor.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.auth.repository.EmployeeRepository;
import com.ptutor.backend.auth.repository.UserRepository;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminBootstrapService implements CommandLineRunner {

    private static final int ADMIN_ROLE = 1;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin.email:admin@ptutor.local}")
    private String adminEmail;

    @Value("${app.security.admin.password:admin}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        String normalizedEmail = AuthService.normalizeEmail(adminEmail);
        if (userRepository.findAnyByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }

        User admin = userRepository.save(User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(adminPassword))
                .status("ACTIVE")
                .build());
        employeeRepository.save(Employee.builder()
                .user(admin)
                .role(ADMIN_ROLE)
                .build());
    }
}
