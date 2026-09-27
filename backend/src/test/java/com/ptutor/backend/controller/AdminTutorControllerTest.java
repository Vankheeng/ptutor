package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminTutorService;

@ExtendWith(MockitoExtension.class)
class AdminTutorControllerTest {

    @Mock AdminTutorService tutorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTutorController(
                tutorService, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void listsTutorsWithManagementFilters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tutors")
                        .queryParam("keyword", "an@example.com")
                        .queryParam("accountStatus", "ACTIVE")
                        .queryParam("profileStatus", "VERIFIED")
                        .queryParam("minRecommendationScore", "60")
                        .queryParam("maxRecommendationScore", "90")
                        .queryParam("sortBy", "recommendationScore")
                        .queryParam("sortDirection", "DESC"))
                .andExpect(status().isOk());

        verify(tutorService).findAll(
                eq("an@example.com"), eq(UserStatus.ACTIVE), eq(TutorProfileStatus.VERIFIED),
                eq(new BigDecimal("60")), eq(new BigDecimal("90")), any(Pageable.class));
    }

    @Test
    void rejectsUnlistedSortField() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(
                get("/api/v1/admin/tutors").queryParam("sortBy", "email")))
                .hasCauseInstanceOf(com.ptutor.backend.exception.ApiException.class);
    }
}
