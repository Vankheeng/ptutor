package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.request.ContractTermsRequest;
import com.ptutor.backend.dto.request.TutorContractCreateRequest;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ContractService;

@ExtendWith(MockitoExtension.class)
class ContractCreationControllerTest {

    @Mock ContractService contractService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc studentMockMvc;
    private MockMvc tutorMockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ApiResponseFactory responseFactory = new ApiResponseFactory(Clock.systemUTC());
        studentMockMvc = MockMvcBuilders.standaloneSetup(new StudentContractController(
                contractService, responseFactory, currentUserProvider)).build();
        tutorMockMvc = MockMvcBuilders.standaloneSetup(new TutorContractController(
                contractService, responseFactory, currentUserProvider)).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void studentCreateUsesSourceIdsAndCurrentUser() throws Exception {
        UUID studyingRequestId = UUID.randomUUID();
        UUID tutorRequestId = UUID.randomUUID();
        when(contractService.createFromTutorStudentRequest(
                eq(userId), eq(studyingRequestId), eq(tutorRequestId), any(ContractTermsRequest.class)))
                .thenReturn(null);

        studentMockMvc.perform(post(
                        "/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}/contracts",
                        studyingRequestId, tutorRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(termsJson()))
                .andExpect(status().isCreated());

        verify(contractService).createFromTutorStudentRequest(
                eq(userId), eq(studyingRequestId), eq(tutorRequestId), any(ContractTermsRequest.class));
    }

    @Test
    void tutorCreateUsesSourceIdsAndCurrentUser() throws Exception {
        UUID teachingRequestId = UUID.randomUUID();
        UUID studentRequestId = UUID.randomUUID();
        when(contractService.createFromStudentTutorRequest(
                eq(userId), eq(teachingRequestId), eq(studentRequestId), any(TutorContractCreateRequest.class)))
                .thenReturn(null);

        tutorMockMvc.perform(post(
                        "/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests/{studentRequestId}/contracts",
                        teachingRequestId, studentRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(termsJson()))
                .andExpect(status().isCreated());

        verify(contractService).createFromStudentTutorRequest(
                eq(userId), eq(teachingRequestId), eq(studentRequestId), any(TutorContractCreateRequest.class));
    }

    private String termsJson() {
        return "{\"price\":200000,\"paymentPeriod\":\"MONTHLY\",\"totalLessons\":16,"
                + "\"preferredSchedule\":\"Monday evening\",\"startDate\":\"2026-10-01\","
                + "\"endDate\":\"2026-12-01\"}";
    }
}
