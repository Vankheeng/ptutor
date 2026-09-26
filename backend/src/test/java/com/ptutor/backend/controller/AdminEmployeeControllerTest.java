package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminEmployeeService;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeControllerTest {

    @Mock AdminEmployeeService employeeService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminEmployeeController(
                employeeService,
                new ApiResponseFactory(Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC)),
                currentUserProvider)).build();
    }

    @Test
    void listsEmployeesWithPagination() throws Exception {
        when(employeeService.findAll(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/admin/employees"))
                .andExpect(status().isOk());

        verify(employeeService).findAll(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void createsEmployeeUsingAuthenticatedAdmin() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(adminUserId);

        mockMvc.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "employee@ptutor.vn",
                                  "initialPassword": "Temporary@123",
                                  "firstName": "Van",
                                  "lastName": "Nguyen",
                                  "phone": "0901234567",
                                  "dateOfBirth": "2000-01-15",
                                  "gender": "MALE",
                                  "citizenId": "012345678901",
                                  "provinceId": "11111111-1111-1111-1111-111111111111",
                                  "districtId": "22222222-2222-2222-2222-222222222222",
                                  "jobFunction": "COMPLAINT_HANDLER"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(employeeService).create(eq(adminUserId), any());
    }

    @Test
    void rejectsInvalidInitialPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "employee@ptutor.vn",
                                  "initialPassword": "short",
                                  "firstName": "Van",
                                  "lastName": "Nguyen",
                                  "phone": "0901234567",
                                  "dateOfBirth": "2000-01-15",
                                  "gender": "MALE",
                                  "citizenId": "012345678901",
                                  "provinceId": "11111111-1111-1111-1111-111111111111",
                                  "districtId": "22222222-2222-2222-2222-222222222222",
                                  "jobFunction": "COMPLAINT_HANDLER"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
