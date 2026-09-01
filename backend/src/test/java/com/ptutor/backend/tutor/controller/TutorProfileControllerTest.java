package com.ptutor.backend.tutor.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.tutor.service.TutorProfileService;

@ExtendWith(MockitoExtension.class)
class TutorProfileControllerTest {

    @Mock TutorProfileService tutorProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TutorProfileController controller = new TutorProfileController(
                tutorProfileService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void getTutorProfileUsesTutorIdRoute() throws Exception {
        UUID tutorId = UUID.randomUUID();
        when(tutorProfileService.findById(tutorId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/tutors/{tutorId}", tutorId))
                .andExpect(status().isOk());

        verify(tutorProfileService).findById(tutorId);
    }

    @Test
    void getMyTutorProfileUsesAuthenticatedUserId() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7f8fc2a0-5a20-490b-9b23-f072f0f0294c")
                .build();
        UUID userId = UUID.fromString(jwt.getSubject());
        when(tutorProfileService.findMine(userId)).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        try {
            mockMvc.perform(get("/api/v1/tutors/me"))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(tutorProfileService).findMine(userId);
    }
}
