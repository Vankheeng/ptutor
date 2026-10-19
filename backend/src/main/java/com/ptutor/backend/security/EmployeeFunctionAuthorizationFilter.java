package com.ptutor.backend.security;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.response.ApiResponseFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EmployeeFunctionAuthorizationFilter extends OncePerRequestFilter {

    private final EmployeeRepository employeeRepository;
    private final ApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Set<EmployeeJobFunction> allowedFunctions = allowedFunctions(request);
        if (allowedFunctions.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String roleClaim = jwt.getToken().getClaimAsString("role");
        if (UserRole.ADMIN.name().equals(roleClaim)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!UserRole.EMPLOYEE.name().equals(roleClaim)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            Employee employee = employeeRepository.findByUser_Id(userId).orElse(null);
            if (employee != null && allowedFunctions.contains(employee.getJobFunction())) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed subject is rejected consistently below.
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), responseFactory.error(
                "EMPLOYEE_FUNCTION_FORBIDDEN",
                "Your employee function does not permit access to this resource",
                Map.of(), request.getRequestURI()));
    }

    private Set<EmployeeJobFunction> allowedFunctions(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/notifications")) {
            return EnumSet.of(EmployeeJobFunction.GENERAL_OPERATIONS);
        }
        if (path.startsWith("/api/v1/admin/complaints")) {
            return EnumSet.of(EmployeeJobFunction.COMPLAINT_HANDLER);
        }
        if (path.startsWith("/api/v1/admin/certificates")
                || path.startsWith("/api/v1/admin/teaching-requests")
                || path.startsWith("/api/v1/admin/tutor-profiles")
                || path.startsWith("/api/v1/admin/subjects")
                || path.startsWith("/api/v1/admin/grades")) {
            return EnumSet.of(EmployeeJobFunction.CONTENT_REVIEWER);
        }
        if (path.startsWith("/api/v1/admin/tutors") && HttpMethod.GET.matches(request.getMethod())) {
            // Content reviewers must read a tutor's profile and certificates to review it.
            return EnumSet.of(EmployeeJobFunction.USER_SUPPORT, EmployeeJobFunction.CONTENT_REVIEWER);
        }
        if (path.startsWith("/api/v1/admin/users") || path.startsWith("/api/v1/admin/tutors")) {
            return EnumSet.of(EmployeeJobFunction.USER_SUPPORT);
        }
        return EnumSet.noneOf(EmployeeJobFunction.class);
    }
}
