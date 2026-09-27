package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.ComplaintRelation;
import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.response.AdminStudentComplaintResponse;
import com.ptutor.backend.dto.response.AdminStudentLearningHistoryResponse;
import com.ptutor.backend.dto.response.AdminStudentLessonResponse;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.mapper.StudentProfileMapper;
import com.ptutor.backend.repository.AdminStudentTransactionRepository;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private final StudentRepository studentRepository;
    private final ContractRepository contractRepository;
    private final LessonRepository lessonRepository;
    private final ComplaintRepository complaintRepository;
    private final AdminStudentTransactionRepository transactionRepository;
    private final StudentProfileMapper studentProfileMapper;
    private final ContractMapper contractMapper;

    @Transactional(readOnly = true)
    public StudentProfileResponse findProfile(UUID userId) {
        Student student = studentRepository.findProfileByUserId(userId)
                .orElseThrow(() -> studentNotFound(userId));
        return studentProfileMapper.toResponse(student);
    }

    @Transactional(readOnly = true)
    public AdminStudentLearningHistoryResponse findLearningHistory(
            UUID userId,
            LessonStatus lessonStatus,
            ContractStatus contractStatus,
            Pageable pageable) {
        requireStudent(userId);
        Page<Lesson> lessons = lessonRepository.findAllForStudentUser(
                userId, lessonStatus, contractStatus, pageable);
        PageResponse<AdminStudentLessonResponse> page = PageResponse.from(
                lessons, lessons.getContent().stream().map(this::toLessonResponse).toList());
        return new AdminStudentLearningHistoryResponse(lessonSummary(userId), page);
    }

    @Transactional(readOnly = true)
    public AdminStudentLessonResponse findLesson(UUID userId, UUID lessonId) {
        requireStudent(userId);
        Lesson lesson = lessonRepository.findDetailedByIdAndStudentUserId(lessonId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND",
                        "Lesson not found: " + lessonId));
        return toLessonResponse(lesson);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> findContracts(
            UUID userId, ContractStatus status, Pageable pageable) {
        requireStudent(userId);
        Page<Contract> contracts = contractRepository.findAllForStudentUser(userId, status, pageable);
        return PageResponse.from(
                contracts, contracts.getContent().stream().map(contractMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ContractResponse findContract(UUID userId, UUID contractId) {
        requireStudent(userId);
        Contract contract = contractRepository.findDetailedByIdAndStudentUserId(contractId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                        "Contract not found: " + contractId));
        return contractMapper.toResponse(contract);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> findTransactions(
            UUID userId,
            FinancialTransactionSource source,
            String status,
            String type,
            String method,
            Pageable pageable) {
        requireStudent(userId);
        Page<FinancialTransactionResponse> transactions = transactionRepository.findAll(
                userId, source, status, type, method, pageable);
        return PageResponse.from(transactions, transactions.getContent());
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponse findTransaction(
            UUID userId, FinancialTransactionSource source, UUID transactionId) {
        requireStudent(userId);
        return transactionRepository.findById(userId, source, transactionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + transactionId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStudentComplaintResponse> findComplaints(
            UUID userId,
            ComplaintRelation relation,
            ComplaintStatus status,
            Pageable pageable) {
        requireStudent(userId);
        ComplaintRelation normalizedRelation = relation == null ? ComplaintRelation.ALL : relation;
        Page<Complaint> complaints = complaintRepository.findAllForParticipant(
                userId, status, normalizedRelation.name(), pageable);
        return PageResponse.from(complaints, complaints.getContent().stream()
                .map(complaint -> toComplaintResponse(complaint, userId))
                .toList());
    }

    private Student requireStudent(UUID userId) {
        return studentRepository.findByUser_Id(userId).orElseThrow(() -> studentNotFound(userId));
    }

    private AdminStudentLearningHistoryResponse.LessonSummary lessonSummary(UUID userId) {
        return new AdminStudentLearningHistoryResponse.LessonSummary(
                lessonRepository.countByContract_Student_User_Id(userId),
                lessonRepository.countByContract_Student_User_IdAndStatus(userId, LessonStatus.SCHEDULED),
                lessonRepository.countByContract_Student_User_IdAndStatus(
                        userId, LessonStatus.PENDING_CONFIRMATION),
                lessonRepository.countByContract_Student_User_IdAndStatus(userId, LessonStatus.CONFIRMED),
                lessonRepository.countByContract_Student_User_IdAndStatus(userId, LessonStatus.COMPLETED),
                lessonRepository.countByContract_Student_User_IdAndStatus(userId, LessonStatus.CANCELLED));
    }

    private AdminStudentLessonResponse toLessonResponse(Lesson lesson) {
        Contract contract = lesson.getContract();
        User tutorUser = contract.getTutor().getUser();
        return new AdminStudentLessonResponse(
                lesson.getId(), contract.getId(), contract.getStatus(),
                contract.getSubject().getId(), contract.getSubject().getName(),
                contract.getGrade().getId(), contract.getGrade().getName(),
                tutorUser.getId(), fullName(tutorUser), tutorUser.getEmail(),
                lesson.getTitle(), lesson.getDate(), lesson.getStartTime(), lesson.getEndTime(),
                lesson.getTeachingMode(), lesson.getStatus(), lesson.getNote(),
                lesson.getCreatedAt(), lesson.getUpdatedAt());
    }

    private AdminStudentComplaintResponse toComplaintResponse(Complaint complaint, UUID userId) {
        User complainant = complaint.getUser();
        Employee reviewer = complaint.getEmployee();
        ComplaintRelation relation = userId.equals(complainant.getId())
                ? ComplaintRelation.SUBMITTED : ComplaintRelation.RECEIVED;
        return new AdminStudentComplaintResponse(
                complaint.getId(), relation, complainant.getId(), fullName(complainant), complainant.getEmail(),
                complaint.getContract().getId(), complaint.getTitle(), complaint.getStatus(),
                reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : fullName(reviewer.getUser()),
                complaint.getCreatedAt(), complaint.getUpdatedAt());
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private ApiException studentNotFound(UUID userId) {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student not found: " + userId);
    }
}
