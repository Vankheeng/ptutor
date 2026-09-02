package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.DistrictResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;

@ExtendWith(MockitoExtension.class)
class DistrictServiceTest {

    @Mock DistrictRepository districtRepository;
    @Mock ProvinceRepository provinceRepository;

    @Test
    void returnsAllDistrictsOrderedByProvinceAndName() {
        Province province = Province.builder().name("Hà Nội").build();
        District district = District.builder()
                .name("Ba Đình")
                .province(province)
                .build();
        when(districtRepository.findAllOrdered()).thenReturn(List.of(district));

        List<DistrictResponse> result = new DistrictService(districtRepository, provinceRepository)
                .findDistricts(null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Ba Đình");
        assertThat(result.getFirst().provinceName()).isEqualTo("Hà Nội");
        verify(districtRepository).findAllOrdered();
        verifyNoInteractions(provinceRepository);
    }

    @Test
    void returnsDistrictsForProvince() {
        UUID provinceId = UUID.randomUUID();
        Province province = Province.builder().name("Hà Nội").build();
        province.setId(provinceId);
        District district = District.builder()
                .name("Ba Đình")
                .province(province)
                .build();
        when(provinceRepository.findById(provinceId)).thenReturn(java.util.Optional.of(province));
        when(districtRepository.findAllByProvinceIdOrdered(provinceId)).thenReturn(List.of(district));

        List<DistrictResponse> result = new DistrictService(districtRepository, provinceRepository)
                .findDistricts(provinceId);

        assertThat(result).extracting(DistrictResponse::provinceId).containsExactly(provinceId);
        verify(provinceRepository).findById(provinceId);
        verify(districtRepository).findAllByProvinceIdOrdered(provinceId);
    }

    @Test
    void rejectsUnknownProvince() {
        UUID provinceId = UUID.randomUUID();
        when(provinceRepository.findById(provinceId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> new DistrictService(districtRepository, provinceRepository)
                .findDistricts(provinceId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("INVALID_PROVINCE");
                });

        verifyNoInteractions(districtRepository);
    }
}
