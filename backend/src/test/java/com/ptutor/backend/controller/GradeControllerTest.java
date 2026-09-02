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
class GradeControllerTest {

    @Mock GradeService gradeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GradeController controller = new GradeController(
                gradeService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getsAllActiveGrades() throws Exception {
        when(gradeService.findActiveGrades()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/grades"))
                .andExpect(status().isOk());

        verify(gradeService).findActiveGrades();
    }
}
