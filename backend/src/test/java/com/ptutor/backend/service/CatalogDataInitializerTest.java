package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.SubjectRepository;

@ExtendWith(MockitoExtension.class)
class CatalogDataInitializerTest {

    @Mock SubjectRepository subjectRepository;
    @Mock GradeRepository gradeRepository;

    private CatalogDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new CatalogDataInitializer(subjectRepository, gradeRepository);
    }

    @Test
    void seedsCulturalSubjectsAndGradesOneToTwelve() {
        when(subjectRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(gradeRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gradeRepository.save(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Subject> subjectCaptor = ArgumentCaptor.forClass(Subject.class);
        ArgumentCaptor<Grade> gradeCaptor = ArgumentCaptor.forClass(Grade.class);
        verify(subjectRepository, times(16)).save(subjectCaptor.capture());
        verify(gradeRepository, times(12)).save(gradeCaptor.capture());
        assertThat(subjectCaptor.getAllValues()).extracting(Subject::getName).contains("Toán", "Ngữ văn");
        assertThat(subjectCaptor.getAllValues()).allMatch(subject -> subject.getStatus() == CatalogStatus.ACTIVE);
        assertThat(gradeCaptor.getAllValues()).extracting(Grade::getName).contains("Lớp 1", "Lớp 12");
        assertThat(gradeCaptor.getAllValues()).extracting(Grade::getLevel).contains(1, 12);
    }

    @Test
    void doesNotOverwriteExistingCatalogRecords() {
        when(subjectRepository.findByNameIgnoreCase(any()))
                .thenReturn(Optional.of(Subject.builder().name("Toán").status(CatalogStatus.INACTIVE).build()));
        when(gradeRepository.findByNameIgnoreCase(any()))
                .thenReturn(Optional.of(Grade.builder().name("Lớp 1").level(1).status(CatalogStatus.INACTIVE).build()));

        initializer.run(new DefaultApplicationArguments());

        verify(subjectRepository, never()).save(any(Subject.class));
        verify(gradeRepository, never()).save(any(Grade.class));
    }
}
