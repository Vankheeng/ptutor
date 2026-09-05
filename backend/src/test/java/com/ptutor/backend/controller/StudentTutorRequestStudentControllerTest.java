package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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

import com.ptutor.backend.dto.request.StudentTutorRequestCreateRequest;
import com.ptutor.backend.dto.request.StudentTutorRequestStatusRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudentTutorRequestService;

@ExtendWith(MockitoExtension.class)
class StudentTutorRequestStudentControllerTest {

    @Mock StudentTutorRequestService studentTutorRequestService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudentTutorRequestStudentController controller = new StudentTutorRequestStudentController(
                studentTutorRequestService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void createUsesCurrentStudentAndTeachingRequestFromRoute() throws Exception {
        UUID teachingRequestId = UUID.randomUUID();
        when(studentTutorRequestService.createForStudent(
                eq(userId), eq(teachingRequestId), any(StudentTutorRequestCreateRequest.class)))
                .thenReturn(null);

        mockMvc.perform(post(
                        "/api/v1/students/me/teaching-requests/{teachingRequestId}/student-tutor-requests",
                        teachingRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gradeId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"proposedPrice\":150000,\"learningMode\":\"ONLINE\"}"))
                .andExpect(status().isCreated());

        verify(studentTutorRequestService).createForStudent(
                eq(userId), eq(teachingRequestId), any(StudentTutorRequestCreateRequest.class));
    }

    @Test
    void listUsesCurrentStudentStatusAndPagination() throws Exception {
        when(studentTutorRequestService.findMineForStudent(
                eq(userId), eq(ApplicationStatus.PENDING), any(Pageable.class)))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/students/me/student-tutor-requests")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(studentTutorRequestService).findMineForStudent(
                eq(userId), eq(ApplicationStatus.PENDING), any(Pageable.class));
    }

    @Test
    void cancelUsesPatchAndCurrentStudentRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(studentTutorRequestService.cancelForStudent(
                userId, requestId, ApplicationStatus.CANCELLED)).thenReturn(null);

        mockMvc.perform(patch("/api/v1/students/me/student-tutor-requests/{requestId}/status", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        verify(studentTutorRequestService).cancelForStudent(
                userId, requestId, ApplicationStatus.CANCELLED);
    }
}
