package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.SubjectResponse;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.repository.SubjectRepository;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock SubjectRepository subjectRepository;

    @Test
    void returnsActiveSubjectsOrderedByRepository() {
        Subject subject = Subject.builder()
                .name("Toán")
                .status(CatalogStatus.ACTIVE)
                .build();
        when(subjectRepository.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE))
                .thenReturn(List.of(subject));

        List<SubjectResponse> result = new SubjectService(subjectRepository).findActiveSubjects();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Toán");
        assertThat(result.getFirst().status()).isEqualTo(CatalogStatus.ACTIVE);
        verify(subjectRepository).findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE);
    }
}
