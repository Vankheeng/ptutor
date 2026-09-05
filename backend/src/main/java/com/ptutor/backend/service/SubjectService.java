package com.ptutor.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.SubjectResponse;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.mapper.SubjectMapper;
import com.ptutor.backend.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final SubjectMapper subjectMapper;

    @Transactional(readOnly = true)
    public List<SubjectResponse> findActiveSubjects() {
        return subjectRepository.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(subjectMapper::toResponse)
                .toList();
    }
}
