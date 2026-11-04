package com.ptutor.backend.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.SupportRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.SupportRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.security.CitizenIdCryptoService;

import lombok.RequiredArgsConstructor;

/**
 * Dữ liệu demo cho trợ lý hỗ trợ (docs/support-chatbot/trien-khai/HUONG-DAN-DEMO.md). Chỉ chạy với Spring profile
 * {@code demo} — không bao giờ ở môi trường thật.
 *
 * <ul>
 *   <li><b>Idempotent theo email</b>: tài khoản đã có thì bỏ qua (khởi động lại không nhân bản dữ liệu, không ghi đè
 *       thay đổi người demo đã làm).</li>
 *   <li><b>Mật khẩu lấy từ biến môi trường</b> {@code DEMO_USER_PASSWORD}; trống thì seeder không làm gì — không có mật
 *       khẩu nào nằm trong mã nguồn hay migration (quyết định 2026-11-03: không dùng Flyway cho dữ liệu demo).</li>
 * </ul>
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String ADMIN_EMAIL = "demo.admin@ptutor.local";
    static final String SUPPORT_EMAIL = "demo.cskh@ptutor.local";
    static final String STUDENT_EMAIL = "demo.hocvien@ptutor.local";
    static final String TUTOR_EMAIL = "demo.giasu@ptutor.local";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final EmployeeRepository employeeRepository;
    private final WalletRepository walletRepository;
    private final DistrictRepository districtRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final CitizenIdCryptoService citizenIdCryptoService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${DEMO_USER_PASSWORD:}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.length() < 8) {
            log.warn("Profile demo đang bật nhưng DEMO_USER_PASSWORD trống hoặc < 8 ký tự: bỏ qua dữ liệu demo");
            return;
        }
        District district = districtRepository.findAllOrdered().stream().findFirst().orElse(null);
        if (district == null) {
            log.warn("Chưa có dữ liệu quận/huyện: bỏ qua dữ liệu demo");
            return;
        }
        ensureEmployee(ADMIN_EMAIL, "Quản trị", "Demo", "099000000001", EmployeeRole.ADMIN, null, district);
        Optional<User> support = ensureEmployee(SUPPORT_EMAIL, "Chăm sóc", "Demo", "099000000002",
                EmployeeRole.EMPLOYEE, EmployeeJobFunction.CUSTOMER_SERVICE, district);
        Optional<User> student = ensureUser(STUDENT_EMAIL, "Lan", "Nguyễn", "099000000003", district, this::makeStudent);
        ensureUser(TUTOR_EMAIL, "An", "Trần", "099000000004", district, this::makeTutor);
        student.ifPresent(user -> support.ifPresent(staff -> seedSupportRequests(user, staff)));
    }

    private Optional<User> ensureEmployee(String email, String firstName, String lastName, String citizenId,
                                          EmployeeRole role, EmployeeJobFunction jobFunction, District district) {
        return ensureUser(email, firstName, lastName, citizenId, district, user -> employeeRepository.saveAndFlush(
                Employee.builder().user(user).role(role).jobFunction(jobFunction).build()));
    }

    /** Tạo người dùng + hồ sơ theo vai trò + ví; email đã có thì trả người dùng sẵn có (không sửa gì). */
    private Optional<User> ensureUser(String email, String firstName, String lastName, String citizenId,
                                      District district, java.util.function.Consumer<User> profile) {
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            return existing;
        }
        String hash = citizenIdCryptoService.hash(citizenId);
        if (userRepository.existsByCitizenIdHash(hash)) {
            log.warn("Số CCCD demo của {} đã được dùng: bỏ qua tài khoản này", email);
            return Optional.empty();
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .encryptedCitizenId(citizenIdCryptoService.encrypt(citizenId))
                .citizenIdHash(hash)
                .password(passwordEncoder.encode(demoPassword))
                .firstName(firstName)
                .lastName(lastName)
                .phone("0900000" + citizenId.substring(citizenId.length() - 3))
                .dateOfBirth(LocalDate.of(1998, 5, 20))
                .gender(Gender.OTHER)
                .district(district)
                .status(UserStatus.ACTIVE)
                .suspensionCount(0)
                .build());
        profile.accept(user);
        walletRepository.save(Wallet.builder().user(user).balance(new BigDecimal("250000"))
                .pendingBalance(BigDecimal.ZERO).build());
        log.info("Đã tạo tài khoản demo {}", email);
        return Optional.of(user);
    }

    private void makeStudent(User user) {
        studentRepository.save(Student.builder().user(user).build());
    }

    private void makeTutor(User user) {
        tutorRepository.save(Tutor.builder()
                .user(user)
                .averageRating(BigDecimal.ZERO)
                .totalReviews(0)
                .completedContractsCount(0)
                .totalStudentsTaught(0)
                .profileStatus(TutorProfileStatus.PENDING)
                .recommendationScore(new BigDecimal("50.00"))
                .scoreFormulaVersion("v1")
                .build());
    }

    /** Hai yêu cầu hỗ trợ để màn hình M17 có dữ liệu: một đã trả lời, một đang chờ. Chỉ tạo khi học viên chưa có. */
    private void seedSupportRequests(User student, User staffUser) {
        if (!supportRequestRepository.findByUser_IdOrderByCreatedAtDesc(student.getId()).isEmpty()) {
            return;
        }
        Employee staff = employeeRepository.findByUser_Id(staffUser.getId()).orElse(null);
        // createdAt do Hibernate tự ghi lúc lưu (không lùi được) → nhận / trả lời lấy cùng "bây giờ", không đặt lùi giờ.
        LocalDateTime now = LocalDateTime.now(clock);
        SupportRequest answered = request(student, "Vì sao khoản nạp ví hôm qua chưa vào số dư?",
                SupportRequestSource.ASSISTANT_ABSTAIN, SupportRequestStatus.ANSWERED);
        answered.setAssignedEmployee(staff);
        answered.setAssignedAt(now);
        answered.setAnswer("Giao dịch đã được đối soát và số dư đã cập nhật. Bạn kiểm tra lại trang Ví giúp mình nhé.");
        answered.setAnsweredAt(now.plusMinutes(1));
        SupportRequest open = request(student, "Tôi muốn đổi email đăng nhập thì làm thế nào?",
                SupportRequestSource.MENU, SupportRequestStatus.OPEN);
        supportRequestRepository.saveAll(List.of(answered, open));
    }

    private SupportRequest request(User student, String question, SupportRequestSource source,
                                   SupportRequestStatus status) {
        return SupportRequest.builder()
                .code(String.format("HT-%06d", supportRequestRepository.nextCodeNumber()))
                .user(student)
                .userRole("STUDENT")
                .question(question)
                .source(source)
                .status(status)
                .build();
    }
}
