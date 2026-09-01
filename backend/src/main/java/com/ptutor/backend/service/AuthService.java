package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.RegistrationRole;
import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.request.LoginRequest;
import com.ptutor.backend.dto.request.RegisterRequest;
import com.ptutor.backend.dto.response.AuthTokenResponse;
import com.ptutor.backend.dto.response.RegisterResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.UserMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.CitizenIdCryptoService;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleResolver roleResolver;
    private final RefreshTokenService refreshTokenService;
    private final CitizenIdCryptoService citizenIdCryptoService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }
        String citizenId = request.citizenId().strip();
        String citizenIdHash = citizenIdCryptoService.hash(citizenId);
        if (userRepository.existsByCitizenIdHash(citizenIdHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "CITIZEN_ID_ALREADY_EXISTS", "Citizen ID is already registered");
        }

        RegistrationRole registrationRole;
        try {
            registrationRole = RegistrationRole.valueOf(request.role().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_ROLE",
                    "Only STUDENT and TUTOR can register");
        }

        provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROVINCE", "Province not found"));
        District district = districtRepository.findById(request.districtId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISTRICT", "District not found"));
        if (district.getProvince() == null
                || !district.getProvince().getId().equals(request.provinceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISTRICT_PROVINCE_MISMATCH",
                    "District does not belong to the selected province");
        }

        User user = userMapper.toUser(request);
        user.setEmail(email);
        user.setEncryptedCitizenId(citizenIdCryptoService.encrypt(citizenId));
        user.setCitizenIdHash(citizenIdHash);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDistrict(district);
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        UserRole role = registrationRole.toUserRole();
        if (role == UserRole.STUDENT) {
            studentRepository.save(Student.builder().user(savedUser).build());
        } else {
            tutorRepository.save(Tutor.builder()
                    .user(savedUser)
                    .averageRating(BigDecimal.ZERO)
                    .totalReviews(0)
                    .completedContractsCount(0)
                    .totalStudentsTaught(0)
                    .build());
        }
        return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), role);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        UserRole role = roleResolver.resolve(user);
        return refreshTokenService.issue(user, role);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
