package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.CertificateService;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CertificateControllerTest {

    @Mock CertificateService certificateService;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        CertificateController controller = new CertificateController(
                certificateService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    void listMineUsesCorrectRouteAndPassesStatusFilter() throws Exception {
        Jwt jwt = jwtFor(userId);
        when(certificateService.findMine(userId, CertificateStatus.REJECTED)).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        try {
            mockMvc.perform(get("/api/v1/tutors/me/certificates")
                            .param("status", "REJECTED"))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(certificateService).findMine(userId, CertificateStatus.REJECTED);
    }

    @Test
    void listTutorCertificatesUsesTutorIdRoute() throws Exception {
        UUID tutorId = UUID.randomUUID();
        when(certificateService.findVerifiedByTutorId(tutorId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tutors/{tutorId}/certificates", tutorId))
                .andExpect(status().isOk());

        verify(certificateService).findVerifiedByTutorId(tutorId);
    }

    private Jwt jwtFor(UUID subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject.toString())
                .build();
    }
}
