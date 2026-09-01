package com.ptutor.backend.tutor.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.TutorProfileResponse;
import com.ptutor.backend.tutor.dto.TutorSelfProfileResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorProfileService {

    private final TutorRepository tutorRepository;

    @Transactional(readOnly = true)
    public TutorProfileResponse findById(UUID tutorId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_NOT_FOUND",
                        "Tutor not found: " + tutorId));
        return TutorProfileResponse.from(tutor);
    }

    @Transactional(readOnly = true)
    public TutorSelfProfileResponse findMine(UUID userId) {
        Tutor tutor = tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Tutor profile not found"));
        return TutorSelfProfileResponse.from(tutor);
    }
}
