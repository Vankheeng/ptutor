package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.AddressResponse;
import com.ptutor.backend.dto.response.AdminTutorDetailResponse;
import com.ptutor.backend.dto.response.AdminTutorLessonResponse;
import com.ptutor.backend.dto.response.AdminTutorReviewResponse;
import com.ptutor.backend.dto.response.AdminTutorSummaryResponse;
import com.ptutor.backend.dto.response.AdminTutorTeachingHistoryResponse;
import com.ptutor.backend.dto.response.CertificateResponse;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorRecommendationScoreResponse;
import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Review;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.CertificateMapper;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.ReviewRepository;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTutorService {

    private final TutorRepository tutorRepository;
    private final CertificateRepository certificateRepository;
    private final ContractRepository contractRepository;
    private final LessonRepository lessonRepository;
    private final ReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final CertificateMapper certificateMapper;
    private final ContractMapper contractMapper;
    private final TutorRecommendationScoreService scoreService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminTutorSummaryResponse> findAll(
            String keyword,
            UserStatus accountStatus,
            TutorProfileStatus profileStatus,
            BigDecimal minScore,
            BigDecimal maxScore,
            Pageable pageable) {
        validateScoreRange(minScore, maxScore);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? "" : keyword.strip();
        Page<Tutor> tutors = tutorRepository.findAllForAdmin(
                accountStatus, profileStatus, minScore, maxScore, normalizedKeyword, pageable);
        return PageResponse.from(tutors, tutors.getContent().stream().map(this::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public AdminTutorDetailResponse findByUserId(UUID userId) {
        return toDetail(requireTutor(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CertificateResponse> findCertificates(
            UUID userId, CertificateStatus status, Pageable pageable) {
        requireTutor(userId);
        Page<Certificate> certificates = certificateRepository.findAllForTutorUser(userId, status, pageable);
        return PageResponse.from(
                certificates, certificates.getContent().stream().map(certificateMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> findContracts(
            UUID userId, ContractStatus status, Pageable pageable) {
        requireTutor(userId);
        Page<Contract> contracts = contractRepository.findAllForTutorUser(userId, status, pageable);
        return PageResponse.from(
                contracts, contracts.getContent().stream().map(contractMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ContractResponse findContract(UUID userId, UUID contractId) {
        requireTutor(userId);
        Contract contract = contractRepository.findDetailedByIdAndTutorUserId(contractId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                        "Contract not found: " + contractId));
        return contractMapper.toResponse(contract);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTutorReviewResponse> findReviews(
            UUID userId, Integer rating, Pageable pageable) {
        requireTutor(userId);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_RATING",
                    "Rating must be between 1 and 5");
        }
        Page<Review> reviews = reviewRepository.findAllForTutorUser(userId, rating, pageable);
        return PageResponse.from(reviews, reviews.getContent().stream().map(this::toReview).toList());
    }

    @Transactional(readOnly = true)
    public AdminTutorTeachingHistoryResponse findTeachingHistory(
            UUID userId,
            LessonStatus lessonStatus,
            ContractStatus contractStatus,
            Pageable pageable) {
        requireTutor(userId);
        Page<Lesson> lessons = lessonRepository.findAllForTutorUser(
                userId, lessonStatus, contractStatus, pageable);
        PageResponse<AdminTutorLessonResponse> page = PageResponse.from(
                lessons, lessons.getContent().stream().map(this::toLesson).toList());
        return new AdminTutorTeachingHistoryResponse(lessonSummary(userId), page);
    }

    @Transactional(readOnly = true)
    public AdminTutorLessonResponse findLesson(UUID userId, UUID lessonId) {
        requireTutor(userId);
        Lesson lesson = lessonRepository.findDetailedByIdAndTutorUserId(lessonId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND",
                        "Lesson not found: " + lessonId));
        return toLesson(lesson);
    }

    @Transactional(readOnly = true)
    public TutorRecommendationScoreResponse findScoreDetails(UUID userId) {
        Tutor tutor = requireTutor(userId);
        return toScoreResponse(tutor);
    }

    @Transactional
    public AdminTutorDetailResponse verifyProfile(UUID reviewerUserId, UUID tutorUserId) {
        Tutor tutor = requirePendingTutorForUpdate(tutorUserId);
        Employee reviewer = requireReviewer(reviewerUserId);
        tutor.setProfileStatus(TutorProfileStatus.VERIFIED);
        tutor.setProfileReviewedBy(reviewer);
        tutor.setProfileReviewedAt(LocalDateTime.now(clock));
        tutor.setProfileRejectionReason(null);
        tutorRepository.saveAndFlush(tutor);
        scoreService.recalculateByTutorId(tutor.getId());
        return toDetail(tutorRepository.findAdminDetailByUserId(tutorUserId).orElseThrow());
    }

    @Transactional
    public AdminTutorDetailResponse rejectProfile(UUID reviewerUserId, UUID tutorUserId, String reason) {
        String normalizedReason = reason == null ? "" : reason.strip();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_REJECTION_REASON",
                    "Profile rejection reason must contain between 10 and 500 characters");
        }
        Tutor tutor = requirePendingTutorForUpdate(tutorUserId);
        Employee reviewer = requireReviewer(reviewerUserId);
        tutor.setProfileStatus(TutorProfileStatus.REJECTED);
        tutor.setProfileReviewedBy(reviewer);
        tutor.setProfileReviewedAt(LocalDateTime.now(clock));
        tutor.setProfileRejectionReason(normalizedReason);
        tutorRepository.saveAndFlush(tutor);
        scoreService.recalculateByTutorId(tutor.getId());
        return toDetail(tutorRepository.findAdminDetailByUserId(tutorUserId).orElseThrow());
    }

    private Tutor requireTutor(UUID userId) {
        return tutorRepository.findAdminDetailByUserId(userId).orElseThrow(() -> tutorNotFound(userId));
    }

    private Tutor requirePendingTutorForUpdate(UUID userId) {
        Tutor tutor = tutorRepository.findByUserIdForUpdate(userId).orElseThrow(() -> tutorNotFound(userId));
        if (tutor.getProfileStatus() != TutorProfileStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "TUTOR_PROFILE_ALREADY_REVIEWED",
                    "Only a PENDING tutor profile can be reviewed");
        }
        return tutor;
    }

    private Employee requireReviewer(UUID reviewerUserId) {
        return employeeRepository.findByUser_Id(reviewerUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only an employee or admin can review tutor profiles"));
    }

    private void validateScoreRange(BigDecimal minScore, BigDecimal maxScore) {
        if (outsideScoreRange(minScore) || outsideScoreRange(maxScore)
                || minScore != null && maxScore != null && minScore.compareTo(maxScore) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECOMMENDATION_SCORE_RANGE",
                    "Recommendation score range must be within 0 and 100 and min must not exceed max");
        }
    }

    private boolean outsideScoreRange(BigDecimal value) {
        return value != null
                && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0);
    }

    private AdminTutorSummaryResponse toSummary(Tutor tutor) {
        User user = tutor.getUser();
        return new AdminTutorSummaryResponse(
                user.getId(), tutor.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhone(), user.getAvatarUrl(), user.getStatus(), tutor.getProfileStatus(),
                tutor.getAverageRating(), tutor.getTotalReviews(), tutor.getRecommendationScore(),
                valueOrZero(user.getSuspensionCount()), tutor.getScoreUpdatedAt(), tutor.getCreatedAt());
    }

    private AdminTutorDetailResponse toDetail(Tutor tutor) {
        User user = tutor.getUser();
        Employee reviewer = tutor.getProfileReviewedBy();
        return new AdminTutorDetailResponse(
                user.getId(), tutor.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getDateOfBirth(), user.getGender(), user.getAvatarUrl(), address(user),
                tutor.getIntroduction(), tutor.getExperienceYears(), tutor.getEducation(), tutor.getTeachingStyleTags(),
                tutor.getTeachingMethodology(), tutor.getStrengthSubjects(), tutor.getTargetStudentType(),
                tutor.getAverageRating(), tutor.getTotalReviews(), tutor.getCompletedContractsCount(),
                tutor.getTotalStudentsTaught(), tutor.getAcceptanceRate(), tutor.getAvgResponseTimeHours(),
                user.getStatus(), tutor.getProfileStatus(), reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : fullName(reviewer.getUser()), tutor.getProfileReviewedAt(),
                tutor.getProfileRejectionReason(), tutor.getRecommendationScore(), tutor.getScoreFormulaVersion(),
                tutor.getScoreUpdatedAt(), tutor.getScoreBreakdown(), user.getSuspensionType(),
                user.getSuspensionReason(), user.getSuspendedAt(), user.getSuspendedUntil(),
                valueOrZero(user.getSuspensionCount()), tutor.getCreatedAt(), tutor.getUpdatedAt());
    }

    private AdminTutorReviewResponse toReview(Review review) {
        User studentUser = review.getStudent() == null ? null : review.getStudent().getUser();
        return new AdminTutorReviewResponse(
                review.getId(), studentUser == null ? null : studentUser.getId(),
                studentUser == null ? null : fullName(studentUser),
                studentUser == null ? null : studentUser.getEmail(),
                review.getRating(), review.getComment(), review.getCreatedAt(), review.getUpdatedAt());
    }

    private AdminTutorLessonResponse toLesson(Lesson lesson) {
        Contract contract = lesson.getContract();
        User studentUser = contract.getStudent().getUser();
        return new AdminTutorLessonResponse(
                lesson.getId(), contract.getId(), contract.getStatus(), contract.getSubject().getId(),
                contract.getSubject().getName(), contract.getGrade().getId(), contract.getGrade().getName(),
                studentUser.getId(), fullName(studentUser), studentUser.getEmail(), lesson.getTitle(),
                lesson.getDate(), lesson.getStartTime(), lesson.getEndTime(), lesson.getTeachingMode(),
                lesson.getMeetingLink(), lesson.getLocation(), lesson.getMaterialsUrl(), lesson.getStatus(),
                lesson.getNote(), lesson.getCreatedAt(), lesson.getUpdatedAt());
    }

    private AdminTutorTeachingHistoryResponse.LessonSummary lessonSummary(UUID userId) {
        return new AdminTutorTeachingHistoryResponse.LessonSummary(
                lessonRepository.countByContract_Tutor_User_Id(userId),
                lessonRepository.countByContract_Tutor_User_IdAndStatus(userId, LessonStatus.SCHEDULED),
                lessonRepository.countByContract_Tutor_User_IdAndStatus(
                        userId, LessonStatus.PENDING_CONFIRMATION),
                lessonRepository.countByContract_Tutor_User_IdAndStatus(userId, LessonStatus.CONFIRMED),
                lessonRepository.countByContract_Tutor_User_IdAndStatus(userId, LessonStatus.COMPLETED),
                lessonRepository.countByContract_Tutor_User_IdAndStatus(userId, LessonStatus.CANCELLED));
    }

    private TutorRecommendationScoreResponse toScoreResponse(Tutor tutor) {
        return new TutorRecommendationScoreResponse(
                tutor.getUser().getId(), tutor.getId(), tutor.getRecommendationScore(),
                tutor.getScoreFormulaVersion(), tutor.getScoreUpdatedAt(), tutor.getScoreBreakdown());
    }

    private AddressResponse address(User user) {
        District district = user.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new AddressResponse(
                user.getDetailAddress(), district == null ? null : district.getId(),
                district == null ? null : district.getName(), province == null ? null : province.getId(),
                province == null ? null : province.getName());
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private ApiException tutorNotFound(UUID userId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_NOT_FOUND", "Tutor not found for user: " + userId);
    }
}
