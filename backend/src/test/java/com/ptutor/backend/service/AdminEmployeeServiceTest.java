package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.CitizenIdCryptoService;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock UserRepository userRepository;
    @Mock ProvinceRepository provinceRepository;
    @Mock DistrictRepository districtRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CitizenIdCryptoService citizenIdCryptoService;
    @Mock RefreshTokenService refreshTokenService;

    private AdminEmployeeService service;
    private UUID adminUserId;
    private UUID employeeId;
    private User employeeUser;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new AdminEmployeeService(
                employeeRepository, userRepository, provinceRepository, districtRepository,
                complaintRepository, passwordEncoder, citizenIdCryptoService, refreshTokenService,
                Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC));
        adminUserId = UUID.randomUUID();
        employeeId = UUID.randomUUID();

        User adminUser = User.builder().status(UserStatus.ACTIVE).build();
        adminUser.setId(adminUserId);
        Employee admin = Employee.builder().user(adminUser).role(EmployeeRole.ADMIN).build();
        when(employeeRepository.findByUser_Id(adminUserId)).thenReturn(Optional.of(admin));

        employeeUser = User.builder()
                .email("employee@ptutor.vn")
                .encryptedCitizenId("encrypted")
                .status(UserStatus.ACTIVE)
                .build();
        employeeUser.setId(UUID.randomUUID());
        employee = Employee.builder()
                .user(employeeUser)
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.COMPLAINT_HANDLER)
                .build();
        employee.setId(employeeId);
    }

    @Test
    void deactivatesEmployeeAndRevokesRefreshTokens() {
        when(employeeRepository.findByIdForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(complaintRepository.existsByEmployee_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(employeeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        when(citizenIdCryptoService.decrypt("encrypted")).thenReturn("012345678901");

        var response = service.deactivate(adminUserId, employeeId, "Employment has ended");

        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(response.maskedCitizenId()).isEqualTo("********8901");
        assertThat(employeeUser.getSuspensionReason()).isEqualTo("Employment has ended");
        verify(refreshTokenService).revokeAllForUser(employeeUser.getId());
    }

    @Test
    void refusesDeactivationWhileEmployeeHasOpenComplaints() {
        when(employeeRepository.findByIdForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(complaintRepository.existsByEmployee_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(employeeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(adminUserId, employeeId, "Employment has ended"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("EMPLOYEE_HAS_OPEN_ASSIGNMENTS");
                });
    }
}
