import { get, patch } from './httpClient';
import type { PageResponse } from './tutorApi';

export type TutorProfileStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
export type CertificateStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';
export type ContractStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type LessonStatus = 'SCHEDULED' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
export type TeachingMode = 'ONLINE' | 'OFFLINE';
export type PaymentPeriod = 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE';

export interface AdminTutorSummary {
  userId: string;
  tutorId: string;
  firstName: string | null;
  lastName: string | null;
  email: string;
  phone: string | null;
  avatarUrl: string | null;
  accountStatus: AccountStatus;
  profileStatus: TutorProfileStatus;
  averageRating: number | null;
  totalReviews: number | null;
  recommendationScore: number | null;
  suspensionCount: number;
  scoreUpdatedAt: string | null;
  createdAt: string;
}

export interface AdminTutorAddress {
  detailAddress: string | null;
  districtId: string | null;
  districtName: string | null;
  provinceId: string | null;
  provinceName: string | null;
}

export interface AdminTutorDetail {
  userId: string;
  tutorId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  dateOfBirth: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  avatarUrl: string | null;
  address: AdminTutorAddress | null;
  introduction: string | null;
  experienceYears: number | null;
  education: string | null;
  teachingStyleTags: string | null;
  teachingMethodology: string | null;
  strengthSubjects: string | null;
  targetStudentType: string | null;
  averageRating: number | null;
  totalReviews: number | null;
  completedContractsCount: number | null;
  totalStudentsTaught: number | null;
  acceptanceRate: number | null;
  avgResponseTimeHours: number | null;
  accountStatus: AccountStatus;
  profileStatus: TutorProfileStatus;
  profileReviewedByEmployeeId: string | null;
  profileReviewedByName: string | null;
  profileReviewedAt: string | null;
  profileRejectionReason: string | null;
  recommendationScore: number | null;
  scoreFormulaVersion: string | null;
  scoreUpdatedAt: string | null;
  suspensionType: 'TEMPORARY' | 'PERMANENT' | null;
  suspensionReason: string | null;
  suspendedAt: string | null;
  suspendedUntil: string | null;
  suspensionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminTutorCertificate {
  id: string;
  tutorId: string;
  name: string;
  issuingOrganization: string | null;
  description: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  certificateUrl: string | null;
  status: CertificateStatus;
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCertificateSummary {
  id: string;
  tutorId: string;
  tutorUserId: string;
  tutorName: string;
  tutorEmail: string;
  name: string;
  issuingOrganization: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  status: CertificateStatus;
  createdAt: string;
}

export interface AdminCertificateDetail extends AdminCertificateSummary {
  tutorAvatarUrl: string | null;
  description: string | null;
  certificateUrl: string | null;
  rejectionReason: string | null;
  reviewedBy: { id: string; name: string } | null;
  reviewedAt: string | null;
  updatedAt: string;
}

export interface AdminTutorContract {
  id: string;
  studentId: string;
  studentFirstName: string | null;
  studentLastName: string | null;
  studentEmail: string;
  subjectName: string;
  gradeName: string;
  teachingMode: TeachingMode;
  price: number;
  paymentPeriod: PaymentPeriod;
  totalLessons: number | null;
  preferredSchedule: string | null;
  startDate: string | null;
  endDate: string | null;
  status: ContractStatus;
  tutorStudentRequestId: string | null;
  studentTutorRequestId: string | null;
  renewedFromContractId: string | null;
  signedAt: string | null;
  createdAt: string;
}

export interface AdminTutorReview {
  reviewId: string;
  studentUserId: string | null;
  studentName: string | null;
  studentEmail: string | null;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface AdminTutorLesson {
  lessonId: string;
  contractId: string;
  contractStatus: ContractStatus;
  subjectName: string;
  gradeName: string;
  studentUserId: string;
  studentName: string;
  studentEmail: string;
  title: string | null;
  date: string;
  startTime: string;
  endTime: string;
  teachingMode: TeachingMode;
  meetingLink: string | null;
  location: string | null;
  materialsUrl: string | null;
  status: LessonStatus;
  note: string | null;
}

export interface AdminTutorTeachingHistory {
  summary: {
    total: number;
    scheduled: number;
    pendingConfirmation: number;
    confirmed: number;
    completed: number;
    cancelled: number;
  };
  lessons: PageResponse<AdminTutorLesson>;
}

export interface AdminTutorScore {
  userId: string;
  tutorId: string;
  recommendationScore: number | null;
  formulaVersion: string | null;
  updatedAt: string | null;
  breakdown: Record<string, number> | null;
}

export interface AdminTutorFilters {
  keyword?: string;
  accountStatus?: AccountStatus;
  profileStatus?: TutorProfileStatus;
  minRecommendationScore?: number;
  maxRecommendationScore?: number;
  sortBy?: 'createdAt' | 'recommendationScore' | 'averageRating';
  sortDirection?: 'ASC' | 'DESC';
  page?: number;
  size?: number;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

const tutorsPath = '/api/v1/admin/tutors';

export function getAdminTutors(filters: AdminTutorFilters, signal?: AbortSignal) {
  return get<PageResponse<AdminTutorSummary>>(`${tutorsPath}${query({ ...filters })}`, { signal });
}

export function getAdminTutor(userId: string, signal?: AbortSignal) {
  return get<AdminTutorDetail>(`${tutorsPath}/${userId}`, { signal });
}

export function getAdminTutorCertificates(userId: string, status?: CertificateStatus, signal?: AbortSignal) {
  return get<PageResponse<AdminTutorCertificate>>(
    `${tutorsPath}/${userId}/certificates${query({ status, size: 100 })}`,
    { signal }
  );
}

export function getAdminTutorContracts(
  userId: string,
  status: ContractStatus | undefined,
  page: number,
  signal?: AbortSignal
) {
  return get<PageResponse<AdminTutorContract>>(`${tutorsPath}/${userId}/contracts${query({ status, page })}`, {
    signal
  });
}

export function getAdminTutorContract(userId: string, contractId: string, signal?: AbortSignal) {
  return get<AdminTutorContract>(`${tutorsPath}/${userId}/contracts/${contractId}`, { signal });
}

export function getAdminTutorReviews(userId: string, rating: number | undefined, page: number, signal?: AbortSignal) {
  return get<PageResponse<AdminTutorReview>>(`${tutorsPath}/${userId}/reviews${query({ rating, page })}`, {
    signal
  });
}

export function getAdminTutorTeachingHistory(
  userId: string,
  filters: { lessonStatus?: LessonStatus; contractStatus?: ContractStatus; page: number },
  signal?: AbortSignal
) {
  return get<AdminTutorTeachingHistory>(`${tutorsPath}/${userId}/teaching-history${query(filters)}`, { signal });
}

export function getAdminTutorLesson(userId: string, lessonId: string, signal?: AbortSignal) {
  return get<AdminTutorLesson>(`${tutorsPath}/${userId}/teaching-history/${lessonId}`, { signal });
}

export function getAdminTutorScore(userId: string, signal?: AbortSignal) {
  return get<AdminTutorScore>(`${tutorsPath}/${userId}/score-details`, { signal });
}

export function approveTutorProfile(userId: string) {
  return patch<AdminTutorDetail>(`/api/v1/admin/tutor-profiles/${userId}/approve`);
}

export function rejectTutorProfile(userId: string, reason: string) {
  return patch<AdminTutorDetail>(`/api/v1/admin/tutor-profiles/${userId}/reject`, { reason });
}

const certificatesPath = '/api/v1/admin/certificates';

export function getAdminCertificates(
  filters: { status: CertificateStatus; keyword?: string; page: number },
  signal?: AbortSignal
) {
  // Certificates of profiles still under review are checked on the profile page instead.
  return get<PageResponse<AdminCertificateSummary>>(
    `${certificatesPath}${query({ ...filters, excludePendingProfiles: 'true' })}`,
    { signal }
  );
}

export function getAdminCertificate(certificateId: string, signal?: AbortSignal) {
  return get<AdminCertificateDetail>(`${certificatesPath}/${certificateId}`, { signal });
}

export function approveCertificate(certificateId: string) {
  return patch<AdminCertificateDetail>(`${certificatesPath}/${certificateId}/approve`);
}

export function rejectCertificate(certificateId: string, rejectionReason: string) {
  return patch<AdminCertificateDetail>(`${certificatesPath}/${certificateId}/reject`, { rejectionReason });
}
