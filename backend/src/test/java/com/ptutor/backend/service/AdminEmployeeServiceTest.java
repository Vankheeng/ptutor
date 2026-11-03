package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
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
    @Mock com.ptutor.backend.repository.RefundRequestRepository refundRequestRepository;
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
                complaintRepository, refundRequestRepository, passwordEncoder, citizenIdCryptoService, refreshTokenService,
                Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC));
        adminUserId = UUID.randomUUID();
        employeeId = UUID.randomUUID();

        User adminUser = User.builder().status(UserStatus.ACTIVE).build();
        adminUser.setId(adminUserId);
        Employee admin = Employee.builder().user(adminUser).role(EmployeeRole.ADMIN).build();
        org.mockito.Mockito.lenient().when(employeeRepository.findByUser_Id(adminUserId)).thenReturn(Optional.of(admin));

        employeeUser = User.builder()
                .email("employee@ptutor.vn")
                .encryptedCitizenId("encrypted")
                .status(UserStatus.ACTIVE)
                .build();
        employeeUser.setId(UUID.randomUUID());
        employee = Employee.builder()
                .user(employeeUser)
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.CUSTOMER_SERVICE)
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
    void listsOpenComplaintsAndRefundRequestsOfEmployee() {
        User complainant = User.builder().firstName("Lan").lastName("Nguyen").email("lan@ptutor.vn").build();
        Complaint complaint = Complaint.builder()
                .user(complainant).title("Tutor missed the lesson").status(ComplaintStatus.AWAITING_EVIDENCE).build();
        complaint.setId(UUID.randomUUID());
        User studentUser = User.builder().email("student@ptutor.vn").build();
        RefundRequest refund = RefundRequest.builder()
                .student(Student.builder().user(studentUser).build()).status(RefundRequestStatus.IN_REVIEW).build();
        refund.setId(UUID.randomUUID());
        when(employeeRepository.findDetailedById(employeeId)).thenReturn(Optional.of(employee));
        when(complaintRepository.findAllByEmployee_IdAndStatusInOrderByCreatedAtAsc(
                org.mockito.ArgumentMatchers.eq(employeeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(complaint));
        when(refundRequestRepository.findAllByEmployee_IdAndStatusOrderByCreatedAtAsc(
                employeeId, RefundRequestStatus.IN_REVIEW))
                .thenReturn(List.of(refund));

        var response = service.findOpenAssignments(employeeId);

        assertThat(response.complaints()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(complaint.getId());
            assertThat(item.complainantName()).isEqualTo("Lan Nguyen");
            assertThat(item.status()).isEqualTo("AWAITING_EVIDENCE");
        });
        assertThat(response.refundRequests()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(refund.getId());
            assertThat(item.studentName()).isEqualTo("student@ptutor.vn");
        });
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
