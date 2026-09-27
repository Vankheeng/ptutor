package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.ComplaintRelation;

@SpringBootTest
@Transactional
class AdminStudentRepositoryIntegrationTest {

    @Autowired AdminStudentTransactionRepository transactionRepository;
    @Autowired ContractRepository contractRepository;
    @Autowired LessonRepository lessonRepository;
    @Autowired ComplaintRepository complaintRepository;
    @Autowired UserRepository userRepository;

    @Test
    void executesStudentManagementQueriesAgainstPostgres() {
        UUID missingUserId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);

        assertThat(transactionRepository.findAll(
                missingUserId, null, null, null, null, pageable)).isEmpty();
        assertThat(contractRepository.findAllForStudentUser(
                missingUserId, null, pageable)).isEmpty();
        assertThat(lessonRepository.findAllForStudentUser(
                missingUserId, null, null, pageable)).isEmpty();
        assertThat(complaintRepository.findAllForParticipant(
                missingUserId, null, ComplaintRelation.ALL.name(), pageable)).isEmpty();
        assertThat(userRepository.findAdminUsers("STUDENT", null, "unmatched-phone", pageable))
                .isEmpty();
    }
}
