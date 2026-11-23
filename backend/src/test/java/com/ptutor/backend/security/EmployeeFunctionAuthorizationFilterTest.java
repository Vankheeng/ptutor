package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/admin/complaints",
            "PATCH, /api/v1/admin/support-requests/00000000-0000-0000-0000-000000000001/answer",
            "POST, /api/v1/admin/notifications",
            "PATCH, /api/v1/admin/tutor-profiles/00000000-0000-0000-0000-000000000001/approve",
            "PATCH, /api/v1/admin/certificates/00000000-0000-0000-0000-000000000001/approve",
            "GET, /api/v1/admin/teaching-requests",
            "PATCH, /api/v1/admin/studying-requests/00000000-0000-0000-0000-000000000001/approve",
            "POST, /api/v1/admin/subjects",
            "POST, /api/v1/admin/grades",
            "GET, /api/v1/admin/users/00000000-0000-0000-0000-000000000001/contracts",
            "PATCH, /api/v1/admin/tutors/00000000-0000-0000-0000-000000000001"
    })
    void allowsCustomerServiceIntoItsModules(String method, String path) throws Exception {
        givenEmployee(EmployeeJobFunction.CUSTOMER_SERVICE);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blocksCustomerServiceFromRefundRequests() throws Exception {
        givenEmployee(EmployeeJobFunction.CUSTOMER_SERVICE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest("GET", "/api/v1/admin/refund-requests"), response, filterChain);

        assertForbidden(response);
    }

    @Test
    void allowsAccountantToHandleRefundRequests() throws Exception {
        givenEmployee(EmployeeJobFunction.ACCOUNTANT);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v1/admin/refund-requests/00000000-0000-0000-0000-000000000001/approve");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/admin/complaints",
            "GET, /api/v1/admin/support-requests",
            "POST, /api/v1/admin/notifications",
            "GET, /api/v1/admin/users",
            "GET, /api/v1/admin/studying-requests",
            "GET, /api/v1/admin/tutors"
    })
    void blocksAccountantFromCustomerServiceModules(String method, String path) throws Exception {
        givenEmployee(EmployeeJobFunction.ACCOUNTANT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest(method, path), response, filterChain);

        assertForbidden(response);
    }

    @Test
    void administratorBypassesEmployeeFunctionCheck() throws Exception {
        authenticate("ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(employeeRepository, never()).findByUser_Id(any());
    }

    private void givenEmployee(EmployeeJobFunction jobFunction) {
        UUID userId = authenticate("EMPLOYEE");
        Employee employee = Employee.builder()
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(jobFunction)
                .build();
        when(employeeRepository.findByUser_Id(userId)).thenReturn(Optional.of(employee));
    }

    private void assertForbidden(MockHttpServletResponse response) throws Exception {
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
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
