package com.ptutor.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ptutor.backend.auth.dto.AuthTokenResponse;
import com.ptutor.backend.auth.dto.LoginRequest;
import com.ptutor.backend.auth.dto.RegisterRequest;
import com.ptutor.backend.auth.dto.RegisterResponse;
import com.ptutor.backend.auth.dto.UserRole;
import com.ptutor.backend.auth.exception.ApiException;
import com.ptutor.backend.auth.mapper.UserMapper;
import com.ptutor.backend.auth.repository.DistrictRepository;
import com.ptutor.backend.auth.repository.ProvinceRepository;
import com.ptutor.backend.auth.repository.StudentRepository;
import com.ptutor.backend.auth.repository.TutorRepository;
import com.ptutor.backend.auth.repository.UserRepository;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.User;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock StudentRepository studentRepository;
    @Mock TutorRepository tutorRepository;
    @Mock ProvinceRepository provinceRepository;
    @Mock DistrictRepository districtRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RoleResolver roleResolver;
    @Mock RefreshTokenService refreshTokenService;

    private AuthService authService;
    private UUID userId;
    private UUID provinceId;
    private UUID districtId;
    private Province province;
    private District district;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, studentRepository, tutorRepository,
                provinceRepository, districtRepository, userMapper, passwordEncoder,
                roleResolver, refreshTokenService);
        userId = UUID.randomUUID();
        provinceId = UUID.randomUUID();
        districtId = UUID.randomUUID();
        province = Province.builder().name("Ha Noi").build();
        province.setId(provinceId);
        district = District.builder().province(province).name("Cau Giay").build();
        district.setId(districtId);
    }

    @Test
    void registerStudentCreatesStudentProfileAndHashesPassword() {
        RegisterRequest request = request("STUDENT");
        User mappedUser = User.builder().build();
        User savedUser = User.builder().email("student@example.com").build();
        savedUser.setId(userId);

        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(district));
        when(userMapper.toUser(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(mappedUser)).thenReturn(savedUser);

        RegisterResponse response = authService.register(request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        verify(studentRepository).save(any());
        assertThat(mappedUser.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(mappedUser.getDistrict()).isEqualTo(district);
    }

    @Test
    void registerTutorCreatesTutorProfile() {
        RegisterRequest request = request("TUTOR");
        User mappedUser = User.builder().build();
        User savedUser = User.builder().email("student@example.com").build();
        savedUser.setId(userId);

        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(district));
        when(userMapper.toUser(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(mappedUser)).thenReturn(savedUser);

        assertThat(authService.register(request).role()).isEqualTo(UserRole.TUTOR);
        verify(tutorRepository).save(any());
    }

    @Test
    void registerRejectsPrivilegedRoles() {
        for (String role : new String[] {"EMPLOYEE", "ADMIN"}) {
            RegisterRequest request = request(role);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Only STUDENT and TUTOR");
        }
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("STUDENT")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerRejectsUnknownProvince() {
        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request("STUDENT")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Province not found");
    }

    @Test
    void registerRejectsDistrictFromAnotherProvince() {
        RegisterRequest request = request("STUDENT");
        Province anotherProvince = Province.builder().build();
        anotherProvince.setId(UUID.randomUUID());
        District anotherDistrict = District.builder().province(anotherProvince).build();
        anotherDistrict.setId(districtId);

        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(anotherDistrict));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void loginIssuesTokenPairForResolvedRole() {
        User user = User.builder()
                .email("student@example.com")
                .password("bcrypt-hash")
                .status("ACTIVE")
                .build();
        user.setId(userId);
        LoginRequest request = new LoginRequest(" STUDENT@EXAMPLE.COM ", "Password123");
        AuthTokenResponse tokenResponse = new AuthTokenResponse(
                "access", "refresh", "Bearer", 3600, 2592000, userId, user.getEmail(), UserRole.STUDENT);

        when(userRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "bcrypt-hash")).thenReturn(true);
        when(roleResolver.resolve(user)).thenReturn(UserRole.STUDENT);
        when(refreshTokenService.issue(user, UserRole.STUDENT)).thenReturn(tokenResponse);

        assertThat(authService.login(request)).isEqualTo(tokenResponse);
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(userRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("student@example.com", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incorrect");
    }

    private RegisterRequest request(String role) {
        return new RegisterRequest(
                "student@example.com", "Password123", role, "Nguyen", "An", "0900000000",
                "MALE", LocalDate.of(2005, 1, 1), provinceId, districtId);
    }
}
