package com.ptutor.backend.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

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
        EmployeeJobFunction requiredFunction = requiredFunction(request.getRequestURI());
        if (requiredFunction == null) {
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
            if (employee != null && employee.getJobFunction() == requiredFunction) {
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

    private EmployeeJobFunction requiredFunction(String path) {
        if (path.startsWith("/api/v1/admin/complaints")) {
            return EmployeeJobFunction.COMPLAINT_HANDLER;
        }
        if (path.startsWith("/api/v1/admin/certificates")
                || path.startsWith("/api/v1/admin/teaching-requests")
                || path.startsWith("/api/v1/admin/subjects")
                || path.startsWith("/api/v1/admin/grades")) {
            return EmployeeJobFunction.CONTENT_REVIEWER;
        }
        if (path.startsWith("/api/v1/admin/users")) {
            return EmployeeJobFunction.USER_SUPPORT;
        }
        return null;
    }
}
