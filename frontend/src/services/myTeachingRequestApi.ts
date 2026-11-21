import { ApiError, get, patch, post, put } from './httpClient';
import type { TeachingRequest } from '../types/api';
import type { ContractTerms, PaymentInitiation, ScheduleConflictCheckResponse } from './studentApi';

export const TEACHING_REQUEST_FEE_PER_STUDENT = 50_000;

export interface TeachingRequestInput {
  subjectId?: string;
  customSubjectName?: string;
  gradeIds: string[];
  title: string;
  note?: string;
  quantity: number;
  districtIds?: string[];
  detailAddress?: string;
  expectedPrice: number;
  teachingMode: 'ONLINE' | 'OFFLINE';
  preferredSchedule?: string;
  description?: string;
  availabilities?: { dayOfWeek: number; startTime: string; endTime: string }[];
}

export interface StudentTeachingApplication {
  id: string;
  studentId: string;
  studentFirstName: string;
  studentLastName: string;
  studentEmail: string;
  gradeId: string;
  gradeName: string;
  teachingRequestId: string;
  contractId: string | null;
  previousContractId: string | null;
  previousContractStatus: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | null;
  proposedPrice: number;
  learningMode: 'ONLINE' | 'OFFLINE';
  preferredSchedule: string | null;
  message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'CONNECTED' | 'REJECTED' | 'CANCELLED';
  nextStep: string | null;
  createdAt: string;
  updatedAt?: string;
}

const base = '/api/v1/tutors/me/teaching-requests';
export const getMyTeachingRequests = (signal?: AbortSignal) => get<TeachingRequest[]>(base, { signal });
export const getMyTeachingRequest = (id: string, signal?: AbortSignal) =>
  get<TeachingRequest>(`${base}/${id}`, { signal });
export const getTeachingRequestApplications = (id: string, signal?: AbortSignal) =>
  get<StudentTeachingApplication[]>(`${base}/${id}/student-requests`, { signal });
export const updateTeachingRequestApplication = (
  teachingRequestId: string,
  requestId: string,
  status: 'ACCEPTED' | 'REJECTED'
) => patch<StudentTeachingApplication>(`${base}/${teachingRequestId}/student-requests/${requestId}/status`, { status });
export const createTeachingRequestContract = (
  teachingRequestId: string,
  studentRequestId: string,
  values: Pick<ContractTerms, 'price' | 'paymentPeriod' | 'startDate' | 'endDate'> &
    Partial<Pick<ContractTerms, 'totalLessons' | 'preferredSchedule' | 'schedules'>> & { gradeId?: string }
) => post<{ id: string }>(`${base}/${teachingRequestId}/student-requests/${studentRequestId}/contracts`, values);
export const checkTeachingRequestContractSchedule = (
  teachingRequestId: string,
  studentRequestId: string,
  values: Pick<ContractTerms, 'price' | 'paymentPeriod' | 'startDate' | 'endDate'> &
    Partial<Pick<ContractTerms, 'totalLessons' | 'preferredSchedule' | 'schedules'>> & { gradeId?: string }
) =>
  post<ScheduleConflictCheckResponse>(
    `${base}/${teachingRequestId}/student-requests/${studentRequestId}/contracts/check-schedule`,
    values
  );
export const createMyTeachingRequest = (values: TeachingRequestInput) => post<TeachingRequest>(base, values);
export const updateMyTeachingRequest = (id: string, values: TeachingRequestInput) =>
  put<TeachingRequest>(`${base}/${id}`, values);
export const updateMyTeachingRequestStatus = (id: string, status: 'OPEN' | 'CLOSED') =>
  patch<TeachingRequest>(`${base}/${id}/status`, { status });
export const cancelMyTeachingRequest = (id: string) => post<TeachingRequest>(`${base}/${id}/cancel`);
export const payTeachingRequestByWallet = (id: string) => post<unknown>(`${base}/${id}/payments/wallet`);
export const startTeachingRequestVnPay = (id: string) =>
  post<PaymentInitiation>(`${base}/${id}/payments/vnpay`, { locale: 'vn' });

export function teachingRequestWalletErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'INSUFFICIENT_WALLET_BALANCE') {
    return 'Số dư ví điện tử không đủ để thanh toán yêu cầu. Vui lòng nạp thêm tiền rồi thử lại.';
  }
  if (error instanceof Error && error.message.trim()) {
    return `Thanh toán bằng ví không thành công: ${error.message}`;
  }
  return 'Thanh toán bằng ví không thành công. Vui lòng thử lại.';
}
