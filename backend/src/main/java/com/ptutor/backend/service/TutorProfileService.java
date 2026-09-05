package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorProfileMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.dto.request.UpdateTutorProfileRequest;
import com.ptutor.backend.dto.response.TutorProfileResponse;
import com.ptutor.backend.dto.response.TutorSelfProfileResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorProfileService {

    private final TutorRepository tutorRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final TutorProfileMapper tutorProfileMapper;

    @Transactional(readOnly = true)
    public TutorProfileResponse findById(UUID tutorId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_NOT_FOUND",
                        "Tutor not found: " + tutorId));
        return tutorProfileMapper.toResponse(tutor);
    }

    @Transactional(readOnly = true)
    public TutorSelfProfileResponse findMine(UUID userId) {
        Tutor tutor = tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Tutor profile not found"));
        return tutorProfileMapper.toSelfResponse(tutor);
    }

    @Transactional
    public TutorSelfProfileResponse updateMine(UUID userId, UpdateTutorProfileRequest request) {
        if (!request.hasAnyUpdate()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_UPDATE_REQUEST",
                    "At least one profile field must be provided");
        }

        Tutor tutor = findMineEntity(userId);
        User user = tutor.getUser();
        updateAddress(request, user);

        setIfProvided(request.firstName(), user::setFirstName);
        setIfProvided(request.lastName(), user::setLastName);
        setIfProvided(request.phone(), user::setPhone);
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        setIfProvided(request.avatarUrl(), user::setAvatarUrl);
        setIfProvided(request.detailAddress(), user::setDetailAddress);
        setIfProvided(request.introduction(), tutor::setIntroduction);
        if (request.experienceYears() != null) {
            tutor.setExperienceYears(request.experienceYears());
        }
        setIfProvided(request.education(), tutor::setEducation);
        setIfProvided(request.teachingStyleTags(), tutor::setTeachingStyleTags);
        setIfProvided(request.teachingMethodology(), tutor::setTeachingMethodology);
        setIfProvided(request.strengthSubjects(), tutor::setStrengthSubjects);
        setIfProvided(request.targetStudentType(), tutor::setTargetStudentType);

        return tutorProfileMapper.toSelfResponse(tutor);
    }

    private Tutor findMineEntity(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Tutor profile not found"));
    }

    private void updateAddress(UpdateTutorProfileRequest request, User user) {
        boolean provinceProvided = request.provinceId() != null;
        boolean districtProvided = request.districtId() != null;
        if (provinceProvided != districtProvided) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ADDRESS_IDS_REQUIRED_TOGETHER",
                    "Province ID and district ID must be provided together");
        }
        if (!provinceProvided) {
            return;
        }

        provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PROVINCE",
                        "Province not found"));
        District district = districtRepository.findById(request.districtId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DISTRICT",
                        "District not found"));
        if (district.getProvince() == null
                || !request.provinceId().equals(district.getProvince().getId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DISTRICT_PROVINCE_MISMATCH",
                    "District does not belong to the selected province");
        }
        user.setDistrict(district);
    }

    private void setIfProvided(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value.strip());
        }
    }
}
