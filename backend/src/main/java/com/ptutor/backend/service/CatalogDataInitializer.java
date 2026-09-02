package com.ptutor.backend.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Creates the default catalog used by teaching requests.
 *
 * <p>The seed is intentionally idempotent: existing records are preserved so
 * an administrator can later rename, deactivate, or extend the catalog
 * without the application startup overwriting those changes.</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogDataInitializer implements ApplicationRunner {

    private static final List<String> DEFAULT_SUBJECTS = List.of(
            "Toán",
            "Ngữ văn",
            "Tiếng Anh",
            "Vật lý",
            "Hóa học",
            "Sinh học",
            "Lịch sử",
            "Địa lý",
            "Giáo dục công dân",
            "Tin học",
            "Công nghệ",
            "Khoa học tự nhiên",
            "Khoa học xã hội",
            "Âm nhạc",
            "Mỹ thuật",
            "Giáo dục thể chất");

    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSubjects();
        seedGrades();
    }

    private void seedSubjects() {
        DEFAULT_SUBJECTS.forEach(name -> subjectRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> subjectRepository.save(Subject.builder()
                        .name(name)
                        .status(CatalogStatus.ACTIVE)
                        .build())));
    }

    private void seedGrades() {
        for (int level = 1; level <= 12; level++) {
            String name = "Lớp " + level;
            int currentLevel = level;
            gradeRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> gradeRepository.save(Grade.builder()
                            .name(name)
                            .level(currentLevel)
                            .status(CatalogStatus.ACTIVE)
                            .build()));
        }
    }
}
