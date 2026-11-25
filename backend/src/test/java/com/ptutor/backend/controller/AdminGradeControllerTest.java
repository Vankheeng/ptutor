package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.GradeService;

@ExtendWith(MockitoExtension.class)
class AdminGradeControllerTest {

    @Mock GradeService gradeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminGradeController(
                gradeService, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void getsAllGradesForAdministration() throws Exception {
        when(gradeService.findAllGrades()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/grades"))
                .andExpect(status().isOk());

        verify(gradeService).findAllGrades();
    }
}
