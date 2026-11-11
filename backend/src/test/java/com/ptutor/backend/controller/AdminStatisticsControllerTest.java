package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.enums.StatisticsGranularity;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminStatisticsService;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsControllerTest {

    @Mock AdminStatisticsService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminStatisticsController(
                service, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void dispatchesFinanceReportWithFilters() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        mockMvc.perform(get("/api/v1/admin/statistics/finance")
                        .param("fromDate", from.toString())
                        .param("toDate", to.toString())
                        .param("timezone", "Asia/Ho_Chi_Minh")
                        .param("granularity", "WEEK"))
                .andExpect(status().isOk());

        verify(service).finance(eq(from), eq(to), eq("Asia/Ho_Chi_Minh"), eq(StatisticsGranularity.WEEK));
    }

    @Test
    void allowsDefaultDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/statistics/overview"))
                .andExpect(status().isOk());

        verify(service).overview(null, null, "Asia/Ho_Chi_Minh", null);
    }
}
