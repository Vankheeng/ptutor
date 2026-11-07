import { get, patch, post, put } from './httpClient';
import type { TeachingRequest } from '../types/api';
import type { PaymentInitiation } from './studentApi';

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
  proposedPrice: number;
  learningMode: 'ONLINE' | 'OFFLINE';
  preferredSchedule: string | null;
  message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
  nextStep: string | null;
  createdAt: string;
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
  values: {
    price: number;
    paymentPeriod: 'PER_LESSON' | 'WEEKLY' | 'MONTHLY' | 'PACKAGE';
    startDate: string;
    endDate: string;
    totalLessons?: number;
    preferredSchedule?: string;
  }
) => post<unknown>(`${base}/${teachingRequestId}/student-requests/${studentRequestId}/contracts`, values);
export const createMyTeachingRequest = (values: TeachingRequestInput) => post<TeachingRequest>(base, values);
export const updateMyTeachingRequest = (id: string, values: TeachingRequestInput) =>
  put<TeachingRequest>(`${base}/${id}`, values);
export const updateMyTeachingRequestStatus = (id: string, status: 'OPEN' | 'CLOSED') =>
  patch<TeachingRequest>(`${base}/${id}/status`, { status });
export const cancelMyTeachingRequest = (id: string) => post<TeachingRequest>(`${base}/${id}/cancel`);
export const payTeachingRequestByWallet = (id: string) => post<unknown>(`${base}/${id}/payments/wallet`);
export const startTeachingRequestVnPay = (id: string) =>
  post<PaymentInitiation>(`${base}/${id}/payments/vnpay`, { locale: 'vn' });
