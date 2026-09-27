package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminStudentService;

@ExtendWith(MockitoExtension.class)
class AdminStudentControllerTest {

    @Mock AdminStudentService studentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminStudentController(
                studentService, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void getsLearningHistoryWithFilters() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/users/{userId}/learning-history", userId)
                        .queryParam("lessonStatus", "COMPLETED")
                        .queryParam("contractStatus", "ACTIVE")
                        .queryParam("page", "1")
                        .queryParam("size", "10"))
                .andExpect(status().isOk());

        verify(studentService).findLearningHistory(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(LessonStatus.COMPLETED),
                org.mockito.ArgumentMatchers.eq(ContractStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void getsTransactionBySource() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/users/{userId}/transactions/{source}/{transactionId}",
                        userId, "PAYMENT", transactionId))
                .andExpect(status().isOk());

        verify(studentService).findTransaction(userId, FinancialTransactionSource.PAYMENT, transactionId);
    }

}
