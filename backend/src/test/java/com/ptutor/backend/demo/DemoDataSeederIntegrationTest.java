package com.ptutor.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.SupportRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.UserRepository;

/** Seeder demo trên PostgreSQL thật: tạo đủ tài khoản theo vai trò và chạy lại không nhân bản dữ liệu. */
@SpringBootTest(properties = "DEMO_USER_PASSWORD=only-for-test-1")
@ActiveProfiles({"prod", "demo"})
@Transactional
class DemoDataSeederIntegrationTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired UserRepository userRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TutorRepository tutorRepository;
    @Autowired SupportRequestRepository supportRequestRepository;

    @Test
    void seedsEachRoleOnceAndIsIdempotentByEmail() {
        long usersBefore = userRepository.count();
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        var admin = userRepository.findByEmailIgnoreCase(DemoDataSeeder.ADMIN_EMAIL).orElseThrow();
        var staff = userRepository.findByEmailIgnoreCase(DemoDataSeeder.SUPPORT_EMAIL).orElseThrow();
        var student = userRepository.findByEmailIgnoreCase(DemoDataSeeder.STUDENT_EMAIL).orElseThrow();
        var tutor = userRepository.findByEmailIgnoreCase(DemoDataSeeder.TUTOR_EMAIL).orElseThrow();
        assertThat(employeeRepository.findByUser_Id(admin.getId()).orElseThrow().getRole()).isEqualTo(EmployeeRole.ADMIN);
        assertThat(employeeRepository.findByUser_Id(staff.getId()).orElseThrow().getJobFunction())
                .isEqualTo(EmployeeJobFunction.CUSTOMER_SERVICE);
        assertThat(studentRepository.findByUser_Id(student.getId())).isPresent();
        assertThat(tutorRepository.findByUser_Id(tutor.getId())).isPresent();
        assertThat(supportRequestRepository.findByUser_IdOrderByCreatedAtDesc(student.getId())).hasSize(2);
        // Lần khởi động trước (cùng DB nháp) có thể đã tạo sẵn: tối đa 4 người dùng mới, không bao giờ nhân bản.
        assertThat(userRepository.count() - usersBefore).isBetween(0L, 4L);
    }
}
