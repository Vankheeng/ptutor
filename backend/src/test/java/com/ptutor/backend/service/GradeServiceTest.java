package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.GradeResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.repository.GradeRepository;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock GradeRepository gradeRepository;

    @Test
    void returnsActiveGradesInLevelOrder() {
        Grade gradeOne = Grade.builder()
                .name("Lớp 1")
                .level(1)
                .status(CatalogStatus.ACTIVE)
                .build();
        Grade gradeTwelve = Grade.builder()
                .name("Lớp 12")
                .level(12)
                .status(CatalogStatus.ACTIVE)
                .build();
        when(gradeRepository.findAllByStatusOrderByLevelAsc(CatalogStatus.ACTIVE))
                .thenReturn(List.of(gradeOne, gradeTwelve));

        List<GradeResponse> result = new GradeService(gradeRepository).findActiveGrades();

        assertThat(result).extracting(GradeResponse::level).containsExactly(1, 12);
        assertThat(result).extracting(GradeResponse::name).containsExactly("Lớp 1", "Lớp 12");
        verify(gradeRepository).findAllByStatusOrderByLevelAsc(CatalogStatus.ACTIVE);
    }
}
