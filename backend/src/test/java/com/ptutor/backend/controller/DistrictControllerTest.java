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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.service.DistrictService;
import com.ptutor.backend.response.ApiResponseFactory;

@ExtendWith(MockitoExtension.class)
class DistrictControllerTest {

    @Mock DistrictService districtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DistrictController controller = new DistrictController(
                districtService, new ApiResponseFactory(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getsAllDistrictsWithoutProvinceFilter() throws Exception {
        when(districtService.findDistricts(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/districts"))
                .andExpect(status().isOk());

        verify(districtService).findDistricts(null);
    }

    @Test
    void getsDistrictsFilteredByProvince() throws Exception {
        UUID provinceId = UUID.randomUUID();
        when(districtService.findDistricts(provinceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/districts")
                .param("provinceId", provinceId.toString()))
                .andExpect(status().isOk());

        verify(districtService).findDistricts(provinceId);
    }
}
