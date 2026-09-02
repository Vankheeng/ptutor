package com.ptutor.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.GradeResponse;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.repository.GradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;

    @Transactional(readOnly = true)
    public List<GradeResponse> findActiveGrades() {
        return gradeRepository.findAllByStatusOrderByLevelAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(GradeResponse::from)
                .toList();
    }
}
