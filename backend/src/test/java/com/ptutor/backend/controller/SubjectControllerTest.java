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

import com.ptutor.backend.dto.response.SubjectResponse;
import com.ptutor.backend.service.SubjectService;
import com.ptutor.backend.response.ApiResponseFactory;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @Mock SubjectService subjectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubjectController controller = new SubjectController(
                subjectService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getsAllActiveSubjects() throws Exception {
        when(subjectService.findActiveSubjects()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/subjects"))
                .andExpect(status().isOk());

        verify(subjectService).findActiveSubjects();
    }
}
