package com.ptutor.backend.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.response.ApiResponseFactory;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EmployeeFunctionAuthorizationFilterTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock ObjectMapper objectMapper;
    @Mock FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsEmployeeWithMatchingFunction() throws Exception {
        UUID userId = authenticate("EMPLOYEE");
        Employee employee = Employee.builder()
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.COMPLAINT_HANDLER)
                .build();
        when(employeeRepository.findByUser_Id(userId)).thenReturn(Optional.of(employee));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/complaints");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blocksEmployeeWithDifferentFunction() throws Exception {
        UUID userId = authenticate("EMPLOYEE");
        Employee employee = Employee.builder()
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.ACCOUNTANT)
                .build();
        when(employeeRepository.findByUser_Id(userId)).thenReturn(Optional.of(employee));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(
                new MockHttpServletRequest("GET", "/api/v1/admin/complaints"), response, filterChain);

        verify(filterChain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void administratorBypassesEmployeeFunctionCheck() throws Exception {
        authenticate("ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(employeeRepository, never()).findByUser_Id(org.mockito.ArgumentMatchers.any());
    }

    private UUID authenticate(String role) {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return userId;
    }

    private EmployeeFunctionAuthorizationFilter filter() {
        return new EmployeeFunctionAuthorizationFilter(
                employeeRepository,
                new ApiResponseFactory(Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC)),
                objectMapper);
    }
}
