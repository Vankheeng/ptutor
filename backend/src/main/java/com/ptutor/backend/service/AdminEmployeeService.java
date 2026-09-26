package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.CreateEmployeeRequest;
import com.ptutor.backend.dto.request.UpdateEmployeeRequest;
import com.ptutor.backend.dto.response.AddressResponse;
import com.ptutor.backend.dto.response.AdminEmployeeDetailResponse;
import com.ptutor.backend.dto.response.AdminEmployeeSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminEmployeeService {

    private static final List<ComplaintStatus> OPEN_COMPLAINT_STATUSES = List.of(
            ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE);

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final ComplaintRepository complaintRepository;
    private final PasswordEncoder passwordEncoder;
    private final CitizenIdCryptoService citizenIdCryptoService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminEmployeeSummaryResponse> findAll(
            UserStatus status,
            EmployeeJobFunction jobFunction,
            String keyword,
            Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<Employee> employees = employeeRepository.findManagedEmployees(
                status, jobFunction, normalizedKeyword, pageable);
        return PageResponse.from(employees, employees.getContent().stream()
                .map(this::toSummary)
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminEmployeeDetailResponse findById(UUID employeeId) {
        return toDetail(findDetailedEmployee(employeeId));
    }

    @Transactional
    public AdminEmployeeDetailResponse create(UUID adminUserId, CreateEmployeeRequest request) {
        requireAdmin(adminUserId);
        String email = AuthService.normalizeEmail(request.email());
        String citizenId = request.citizenId().strip();
        ensureUniqueIdentity(null, email, citizenId);
        District district = validateAddress(request.provinceId(), request.districtId());

        User user = User.builder()
                .email(email)
                .encryptedCitizenId(citizenIdCryptoService.encrypt(citizenId))
                .citizenIdHash(citizenIdCryptoService.hash(citizenId))
                .password(passwordEncoder.encode(request.initialPassword()))
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .phone(request.phone().strip())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .district(district)
                .detailAddress(normalizeOptional(request.detailAddress()))
                .status(UserStatus.ACTIVE)
                .suspensionCount(0)
                .build();
        User savedUser = userRepository.save(user);
        Employee savedEmployee = employeeRepository.saveAndFlush(Employee.builder()
                .user(savedUser)
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(request.jobFunction())
                .build());
        return toDetail(savedEmployee);
    }

    @Transactional
    public AdminEmployeeDetailResponse update(
            UUID adminUserId,
            UUID employeeId,
            UpdateEmployeeRequest request) {
        requireAdmin(adminUserId);
        Employee employee = findManagedEmployeeForUpdate(employeeId);
        User user = employee.getUser();
        String email = AuthService.normalizeEmail(request.email());
        String citizenId = request.citizenId() == null ? null : request.citizenId().strip();
        ensureUniqueEmail(user.getId(), email);
        if (citizenId != null) {
            ensureUniqueCitizenId(user.getId(), citizenId);
        }
        ensureFunctionCanChange(employee, request.jobFunction());
        District district = validateAddress(request.provinceId(), request.districtId());

        user.setEmail(email);
        if (citizenId != null) {
            user.setEncryptedCitizenId(citizenIdCryptoService.encrypt(citizenId));
            user.setCitizenIdHash(citizenIdCryptoService.hash(citizenId));
        }
        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        user.setPhone(request.phone().strip());
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(request.gender());
        user.setDistrict(district);
        user.setDetailAddress(normalizeOptional(request.detailAddress()));
        employee.setJobFunction(request.jobFunction());

        userRepository.save(user);
        return toDetail(employeeRepository.saveAndFlush(employee));
    }

    @Transactional
    public AdminEmployeeDetailResponse deactivate(UUID adminUserId, UUID employeeId, String reason) {
        User admin = requireAdmin(adminUserId).getUser();
        Employee employee = findManagedEmployeeForUpdate(employeeId);
        User user = employee.getUser();
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_ALREADY_INACTIVE",
                    "The employee account is already inactive");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_NOT_ACTIVE",
                    "Only an active employee account can be deactivated");
        }
        ensureNoOpenAssignments(employee);

        user.setStatus(UserStatus.INACTIVE);
        user.setSuspensionType(null);
        user.setSuspensionReason(normalizeReason(reason));
        user.setSuspendedAt(now());
        user.setSuspendedUntil(null);
        user.setSuspendedByUser(admin);
        user.setReactivatedAt(null);
        user.setReactivatedByUser(null);
        user.setReactivationReason(null);
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(user.getId());
        return toDetail(employee);
    }

    @Transactional
    public AdminEmployeeDetailResponse reactivate(UUID adminUserId, UUID employeeId, String reason) {
        User admin = requireAdmin(adminUserId).getUser();
        Employee employee = findManagedEmployeeForUpdate(employeeId);
        User user = employee.getUser();
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_ALREADY_ACTIVE",
                    "The employee account is already active");
        }
        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_REACTIVATION_NOT_ALLOWED",
                    "Only an inactive employee account can be reactivated");
        }
        if (employee.getJobFunction() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_FUNCTION_REQUIRED",
                    "An employee job function is required before reactivation");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setReactivatedAt(now());
        user.setReactivatedByUser(admin);
        user.setReactivationReason(normalizeReason(reason));
        userRepository.saveAndFlush(user);
        return toDetail(employee);
    }

    private Employee requireAdmin(UUID adminUserId) {
        Employee admin = employeeRepository.findByUser_Id(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                        "Only an administrator can manage employees"));
        if (admin.getRole() != EmployeeRole.ADMIN || admin.getUser() == null
                || admin.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "Only an active administrator can manage employees");
        }
        return admin;
    }

    private Employee findDetailedEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findDetailedById(employeeId)
                .orElseThrow(() -> employeeNotFound(employeeId));
        ensureManagedEmployee(employee, employeeId);
        return employee;
    }

    private Employee findManagedEmployeeForUpdate(UUID employeeId) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> employeeNotFound(employeeId));
        ensureManagedEmployee(employee, employeeId);
        return employee;
    }

    private void ensureManagedEmployee(Employee employee, UUID employeeId) {
        if (employee.getRole() != EmployeeRole.EMPLOYEE || employee.getUser() == null) {
            throw employeeNotFound(employeeId);
        }
    }

    private District validateAddress(UUID provinceId, UUID districtId) {
        provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROVINCE",
                        "Province not found"));
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISTRICT",
                        "District not found"));
        if (district.getProvince() == null || !provinceId.equals(district.getProvince().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISTRICT_PROVINCE_MISMATCH",
                    "District does not belong to the selected province");
        }
        return district;
    }

    private void ensureUniqueIdentity(UUID currentUserId, String email, String citizenId) {
        ensureUniqueEmail(currentUserId, email);
        ensureUniqueCitizenId(currentUserId, citizenId);
    }

    private void ensureUniqueEmail(UUID currentUserId, String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                        "Email is already registered");
            }
        });
    }

    private void ensureUniqueCitizenId(UUID currentUserId, String citizenId) {
        String citizenIdHash = citizenIdCryptoService.hash(citizenId);
        userRepository.findByCitizenIdHash(citizenIdHash).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new ApiException(HttpStatus.CONFLICT, "CITIZEN_ID_ALREADY_EXISTS",
                        "Citizen ID is already registered");
            }
        });
    }

    private void ensureFunctionCanChange(Employee employee, EmployeeJobFunction newFunction) {
        if (employee.getJobFunction() == EmployeeJobFunction.COMPLAINT_HANDLER
                && newFunction != EmployeeJobFunction.COMPLAINT_HANDLER) {
            ensureNoOpenAssignments(employee);
        }
    }

    private void ensureNoOpenAssignments(Employee employee) {
        if (complaintRepository.existsByEmployee_IdAndStatusIn(employee.getId(), OPEN_COMPLAINT_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_HAS_OPEN_ASSIGNMENTS",
                    "Reassign the employee's open complaints before changing access");
        }
    }

    private AdminEmployeeSummaryResponse toSummary(Employee employee) {
        User user = employee.getUser();
        return new AdminEmployeeSummaryResponse(
                employee.getId(), user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), employee.getJobFunction(), user.getStatus(), instant(employee.getCreatedAt()),
                instant(latest(employee.getUpdatedAt(), user.getUpdatedAt())));
    }

    private AdminEmployeeDetailResponse toDetail(Employee employee) {
        User user = employee.getUser();
        District district = user.getDistrict();
        AddressResponse address = new AddressResponse(
                user.getDetailAddress(),
                district == null ? null : district.getId(),
                district == null ? null : district.getName(),
                district == null || district.getProvince() == null ? null : district.getProvince().getId(),
                district == null || district.getProvince() == null ? null : district.getProvince().getName());
        return new AdminEmployeeDetailResponse(
                employee.getId(), user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getDateOfBirth(), user.getGender(), maskedCitizenId(user), address,
                employee.getRole(), employee.getJobFunction(), user.getStatus(), user.getSuspensionReason(),
                instant(user.getSuspendedAt()), idOf(user.getSuspendedByUser()), user.getReactivationReason(),
                instant(user.getReactivatedAt()), idOf(user.getReactivatedByUser()),
                instant(employee.getCreatedAt()), instant(latest(employee.getUpdatedAt(), user.getUpdatedAt())));
    }

    private String maskedCitizenId(User user) {
        String citizenId = citizenIdCryptoService.decrypt(user.getEncryptedCitizenId());
        return citizenId.length() <= 4 ? citizenId : "*".repeat(citizenId.length() - 4)
                + citizenId.substring(citizenId.length() - 4);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.length() < 10 || normalized.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMPLOYEE_ACCESS_REASON",
                    "Reason must contain between 10 and 1000 characters");
        }
        return normalized;
    }

    private UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ApiException employeeNotFound(UUID employeeId) {
        return new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND",
                "Employee not found: " + employeeId);
    }
}
