package com.ptutor.backend.tutor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.tutor.dto.TutorStudentRequestRequest;
import com.ptutor.backend.tutor.service.TutorStudentRequestService;

@ExtendWith(MockitoExtension.class)
class TutorStudentRequestControllerTest {

    @Mock TutorStudentRequestService tutorStudentRequestService;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        TutorStudentRequestController controller = new TutorStudentRequestController(
                tutorStudentRequestService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    void createsTutorProposalFromAuthenticatedTutor() throws Exception {
        when(tutorStudentRequestService.create(eq(userId), any(TutorStudentRequestRequest.class)))
                .thenReturn(null);

        setTutorAuthentication();
        mockMvc.perform(post("/api/v1/tutors/me/tutor-student-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studyingRequestId": "11111111-1111-1111-1111-111111111111",
                          "gradeId": "22222222-2222-2222-2222-222222222222",
                          "proposedPrice": 150000,
                          "teachingMode": "ONLINE",
                          "preferredSchedule": "Weekday evenings",
                          "message": "I can help the student."
                        }
                        """))
                .andExpect(status().isCreated());

        verify(tutorStudentRequestService).create(eq(userId), any(TutorStudentRequestRequest.class));
    }

    @Test
    void listsOwnTutorProposalsWithStatusFilter() throws Exception {
        when(tutorStudentRequestService.findMine(userId, ApplicationStatus.PENDING)).thenReturn(List.of());

        setTutorAuthentication();
        mockMvc.perform(get("/api/v1/tutors/me/tutor-student-requests")
                .param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).findMine(userId, ApplicationStatus.PENDING);
    }

    @Test
    void cancelsOwnTutorProposal() throws Exception {
        UUID proposalId = UUID.randomUUID();
        when(tutorStudentRequestService.cancel(userId, proposalId)).thenReturn(null);

        setTutorAuthentication();
        mockMvc.perform(post("/api/v1/tutors/me/tutor-student-requests/{proposalId}/cancel", proposalId))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).cancel(userId, proposalId);
    }

    private void setTutorAuthentication() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
