import { get, patch, post } from './httpClient';
import type { TeachingRequest } from '../types/api';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type RequestStatus = 'DRAFT' | 'OPEN' | 'MATCHED' | 'CLOSED' | 'CANCELLED';
export type ApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
export type LearningMode = 'ONLINE' | 'OFFLINE';
export type ContractStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface StudentAddress {
  detailAddress: string | null;
  districtId: string | null;
  districtName: string | null;
  provinceId: string | null;
  provinceName: string | null;
}

export interface StudentProfile {
  userId: string;
  studentId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  dateOfBirth: string | null;
  avatarUrl: string | null;
  address: StudentAddress | null;
  introduction: string | null;
  learningStyle: string | null;
  personalityTags: string | null;
  goalsDescription: string | null;
  currentLevel: string | null;
  weakPoints: string | null;
}

export interface Availability {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface StudyingRequest {
  id: string;
  studentId: string;
  subjectId: string;
  subjectName: string;
  gradeId: string;
  gradeName: string;
  quantity: number;
  districtId: string | null;
  districtName: string | null;
  title: string;
  description: string | null;
  note: string | null;
  detailAddress: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  learningGoals: string | null;
  learningMode: LearningMode;
  preferredSchedule: string | null;
  availabilities: Availability[];
  status: RequestStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TutorOffer {
  id: string;
  tutorId: string;
  tutorFirstName: string | null;
  tutorLastName: string | null;
  tutorEmail: string;
  studyingRequestId: string;
  studyingRequestTitle: string | null;
  subjectId: string;
  subjectName: string;
  gradeId: string;
  gradeName: string;
  proposedPrice: number;
  teachingMode: LearningMode;
  preferredSchedule: string | null;
  message: string | null;
  status: ApplicationStatus;
  nextStep: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StudentApplication {
  id: string;
  teachingRequestId: string;
  teachingRequestTitle: string | null;
  subjectId: string | null;
  subjectName: string | null;
  customSubjectName: string | null;
  tutorId: string;
  tutorFirstName: string | null;
  tutorLastName: string | null;
  tutorEmail: string;
  gradeId: string;
  gradeName: string;
  proposedPrice: number;
  learningMode: LearningMode;
  preferredSchedule: string | null;
  message: string | null;
  status: ApplicationStatus;
  nextStep: string | null;
  createdAt: string;
  updatedAt: string;
}

export type PaymentPeriod = 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE';

export interface Contract {
  id: string;
  studentId: string;
  studentFirstName: string | null;
  studentLastName: string | null;
  studentEmail: string;
  tutorId: string;
  tutorFirstName: string | null;
  tutorLastName: string | null;
  tutorEmail: string;
  subjectId: string;
  subjectName: string;
  gradeId: string;
  gradeName: string;
  teachingMode: LearningMode;
  price: number;
  paymentPeriod: PaymentPeriod;
  totalLessons: number;
  preferredSchedule: string;
  startDate: string;
  endDate: string;
  status: ContractStatus;
  createdByUserId: string;
  signedByUserId: string | null;
  tutorStudentRequestId: string | null;
  studentTutorRequestId: string | null;
  renewedFromContractId: string | null;
  signedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Notification {
  id: string;
  title: string;
  content: string;
  type: string;
  eventType: string | null;
  referenceType: string | null;
  referenceId: string | null;
  isRead: boolean;
  createdAt: string;
}

export interface WalletBalance {
  walletId: string;
  userId: string;
  balance: number;
  pendingBalance: number;
  currency: string;
  updatedAt: string;
}

export interface FinancialTransaction {
  id: string;
  source: string;
  type: string;
  method: string;
  status: string;
  amount: number;
  referenceId: string | null;
  description: string | null;
  occurredAt: string;
}

export interface Installment {
  id: string;
  sequenceNumber: number;
  paymentPeriod: PaymentPeriod;
  amount: number;
  dueDate: string;
  lessonId: string | null;
  status: 'PENDING' | 'PAID' | 'CANCELLED';
}

export interface Payment {
  id: string;
  paymentType: string;
  paymentMethod: string;
  status: string;
  amount: number;
  referenceType: string;
  referenceId: string;
  paymentInstallmentId: string | null;
  transactionCode: string | null;
  providerTransactionNo: string | null;
  expiresAt: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface PaymentInitiation {
  paymentId: string;
  paymentType: string;
  amount: number;
  status: string;
  paymentUrl: string;
  expiresAt: string;
}

export interface StudentProfileUpdate {
  firstName?: string;
  lastName?: string;
  phone?: string;
  gender?: StudentProfile['gender'];
  dateOfBirth?: string;
  avatarUrl?: string;
  detailAddress?: string;
  provinceId?: string;
  districtId?: string;
  introduction?: string;
  learningStyle?: string;
  personalityTags?: string;
  goalsDescription?: string;
  currentLevel?: string;
  weakPoints?: string;
}

export interface StudyingRequestInput {
  subjectId: string;
  gradeId: string;
  quantity: number;
  title: string;
  description?: string;
  note?: string;
  districtId?: string;
  detailAddress?: string;
  minPrice?: number;
  maxPrice?: number;
  learningGoals?: string;
  learningMode: LearningMode;
  preferredSchedule?: string;
  availabilities?: Availability[];
}

export interface ContractTerms {
  price: number;
  paymentPeriod: PaymentPeriod;
  totalLessons: number;
  preferredSchedule: string;
  startDate: string;
  endDate: string;
}

const pageParams = (page = 0, size = 20, status?: string) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return params.toString();
};

export const getStudentProfile = (signal?: AbortSignal) => get<StudentProfile>('/api/v1/students/me', { signal });
export const updateStudentProfile = (values: StudentProfileUpdate) =>
  patch<StudentProfile>('/api/v1/students/me', values);

export function getMyStudyingRequests(status?: RequestStatus, page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<StudyingRequest>>(`/api/v1/students/me/studying-requests?${pageParams(page, size, status)}`, {
    signal
  });
}
export const getMyStudyingRequest = (id: string) => get<StudyingRequest>(`/api/v1/students/me/studying-requests/${id}`);
export const createStudyingRequest = (values: StudyingRequestInput) =>
  post<StudyingRequest>('/api/v1/students/me/studying-requests', values);
export const updateStudyingRequest = (id: string, values: Partial<StudyingRequestInput>) =>
  patch<StudyingRequest>(`/api/v1/students/me/studying-requests/${id}`, values);
export const updateStudyingRequestStatus = (id: string, status: RequestStatus) =>
  patch<StudyingRequest>(`/api/v1/students/me/studying-requests/${id}/status`, { status });
export const cancelStudyingRequest = (id: string) =>
  post<StudyingRequest>(`/api/v1/students/me/studying-requests/${id}/cancel`);

export function getTutorOffers(
  studyingRequestId: string,
  status?: ApplicationStatus,
  page = 0,
  size = 20,
  signal?: AbortSignal
) {
  return get<PageResponse<TutorOffer>>(
    `/api/v1/students/me/studying-requests/${studyingRequestId}/tutor-requests?${pageParams(page, size, status)}`,
    { signal }
  );
}
export const getTutorOffer = (requestId: string, offerId: string) =>
  get<TutorOffer>(`/api/v1/students/me/studying-requests/${requestId}/tutor-requests/${offerId}`);
export const acceptTutorOffer = (requestId: string, offerId: string) =>
  patch<TutorOffer>(`/api/v1/students/me/studying-requests/${requestId}/tutor-requests/${offerId}/accept`);
export const rejectTutorOffer = (requestId: string, offerId: string) =>
  patch<TutorOffer>(`/api/v1/students/me/studying-requests/${requestId}/tutor-requests/${offerId}/reject`);

export const getTeachingRequests = (signal?: AbortSignal) =>
  get<TeachingRequest[]>('/api/v1/teaching-requests', { signal });
export const getTeachingRequest = (id: string) => get<TeachingRequest>(`/api/v1/teaching-requests/${id}`);
export const createStudentApplication = (
  teachingRequestId: string,
  values: {
    gradeId: string;
    proposedPrice: number;
    learningMode: LearningMode;
    preferredSchedule?: string;
    message?: string;
  }
) =>
  post<StudentApplication>(`/api/v1/students/me/teaching-requests/${teachingRequestId}/student-tutor-requests`, values);
export function getStudentApplications(status?: ApplicationStatus, page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<StudentApplication>>(
    `/api/v1/students/me/student-tutor-requests?${pageParams(page, size, status)}`,
    { signal }
  );
}
export const cancelStudentApplication = (id: string) =>
  patch<StudentApplication>(`/api/v1/students/me/student-tutor-requests/${id}/status`, { status: 'CANCELLED' });

export const createStudentContract = (studyingRequestId: string, tutorRequestId: string, terms: ContractTerms) =>
  post<Contract>(
    `/api/v1/students/me/studying-requests/${studyingRequestId}/tutor-requests/${tutorRequestId}/contracts`,
    terms
  );
export function getContracts(status?: ContractStatus, page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<Contract>>(`/api/v1/users/me/contracts?${pageParams(page, size, status)}`, { signal });
}
export const getContract = (id: string) => get<Contract>(`/api/v1/users/me/contracts/${id}`);
export const updateContract = (id: string, values: Partial<ContractTerms> & { gradeId?: string }) =>
  patch<Contract>(`/api/v1/users/me/contracts/${id}`, values);
export const signContract = (id: string) => patch<Contract>(`/api/v1/users/me/contracts/${id}/sign`);
export const rejectContract = (id: string) => patch<Contract>(`/api/v1/users/me/contracts/${id}/reject`);
export const cancelContract = (id: string) => patch<Contract>(`/api/v1/users/me/contracts/${id}/cancel`);
export const renewContract = (id: string, terms: ContractTerms) =>
  post<Contract>(`/api/v1/users/me/contracts/${id}/renewals`, terms);

export const confirmLesson = (id: string) => post(`/api/v1/students/me/lessons/${id}/confirm`);

export function getNotifications(status?: 'READ' | 'UNREAD', page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<Notification>>(`/api/v1/users/me/notifications?${pageParams(page, size, status)}`, {
    signal
  });
}
export const markNotificationRead = (id: string) => patch<Notification>(`/api/v1/users/me/notifications/${id}/read`);
export const markAllNotificationsRead = () => patch<{ markedCount: number }>('/api/v1/users/me/notifications/read-all');
export const getUnreadCount = (signal?: AbortSignal) =>
  get<{ unreadCount: number }>('/api/v1/users/me/notifications/unread-count', { signal });

export const getWalletBalance = (signal?: AbortSignal) => get<WalletBalance>('/api/v1/users/me/wallet', { signal });
export function getWalletTransactions(page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<Record<string, unknown>>>(`/api/v1/users/me/wallet/transactions?page=${page}&size=${size}`, {
    signal
  });
}
export function getFinancialTransactions(page = 0, size = 20, signal?: AbortSignal) {
  return get<PageResponse<FinancialTransaction>>(`/api/v1/users/me/transactions?page=${page}&size=${size}`, { signal });
}
export const getPayment = (id: string) => get<Payment>(`/api/v1/users/me/payments/${id}`);
export const getInstallments = (contractId: string) =>
  get<Installment[]>(`/api/v1/students/me/contracts/${contractId}/payment-installments`);
export const createWalletTopUp = (amount: number) =>
  post<PaymentInitiation>('/api/v1/users/me/wallet/top-ups/vnpay', { amount, locale: 'vn' });
export const payStudyingRequest = (id: string) =>
  post<PaymentInitiation>(`/api/v1/students/me/studying-requests/${id}/payments/vnpay`, { locale: 'vn' });
export const payInstallment = (contractId: string, installmentId: string) =>
  post<PaymentInitiation>(
    `/api/v1/students/me/contracts/${contractId}/payment-installments/${installmentId}/payments/vnpay`,
    { locale: 'vn' }
  );
