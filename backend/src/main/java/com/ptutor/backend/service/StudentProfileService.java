package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.UpdateStudentProfileRequest;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentProfileMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentRepository studentRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final StudentProfileMapper studentProfileMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public StudentProfileResponse getCurrentProfile() {
        return studentProfileMapper.toResponse(findCurrentStudent());
    }

    @Transactional
    public StudentProfileResponse updateCurrentProfile(UpdateStudentProfileRequest request) {
        if (!request.hasAnyUpdate()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_UPDATE_REQUEST",
                    "At least one profile field must be provided");
        }

        Student student = findCurrentStudent();
        User user = student.getUser();
        updateAddress(request, user);
        studentProfileMapper.updateUser(request, user);
        studentProfileMapper.updateStudent(request, student);
        return studentProfileMapper.toResponse(student);
    }

    private Student findCurrentStudent() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        return studentRepository.findProfileByUserId(currentUserId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STUDENT_PROFILE_NOT_FOUND",
                        "Student profile not found"));
    }

    private void updateAddress(UpdateStudentProfileRequest request, User user) {
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
}
