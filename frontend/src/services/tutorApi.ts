import { del, get, patch, post, put } from './httpClient';

export interface TutorAddress {
  detailAddress: string | null;
  districtId: string | null;
  districtName: string | null;
  provinceId: string | null;
  provinceName: string | null;
}

export interface TutorProfile {
  tutorId: string;
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  dateOfBirth: string | null;
  avatarUrl: string | null;
  address: TutorAddress | null;
  introduction: string | null;
  experienceYears: number | null;
  education: string | null;
  teachingStyleTags: string | null;
  teachingMethodology: string | null;
  strengthSubjects: string | null;
  targetStudentType: string | null;
  averageRating: number | null;
  totalReviews: number;
  completedContractsCount: number;
  totalStudentsTaught: number;
  acceptanceRate: number | null;
  avgResponseTimeHours: number | null;
}

export interface TutorCertificate {
  id: string;
  tutorId: string;
  name: string;
  issuingOrganization: string | null;
  description: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  certificateUrl: string | null;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface TutorTeachingProposal {
  id: string;
  studyingRequestId: string;
  studyingRequestTitle: string | null;
  subjectName: string;
  gradeName: string;
  proposedPrice: number;
  teachingMode: 'ONLINE' | 'OFFLINE';
  preferredSchedule: string | null;
  message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
  nextStep: string | null;
  createdAt: string;
}

export interface TutorContract {
  id: string;
  studentFirstName: string | null;
  studentLastName: string | null;
  subjectName: string | null;
  gradeName: string | null;
  teachingMode: 'ONLINE' | 'OFFLINE';
  price: number;
  startDate: string;
  endDate: string;
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
}

export interface TutorLesson {
  id: string;
  contractId: string;
  title: string;
  date: string;
  startTime: string;
  endTime: string;
  teachingMode: 'ONLINE' | 'OFFLINE';
  status: 'SCHEDULED' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
  note: string | null;
}

export function getTutorProfile(signal?: AbortSignal) {
  return get<TutorProfile>('/api/v1/tutors/me', { signal });
}

export function updateTutorProfile(values: Record<string, unknown>) {
  return patch<TutorProfile>('/api/v1/tutors/me', values);
}

export function getTutorCertificates(signal?: AbortSignal) {
  return get<TutorCertificate[]>('/api/v1/tutors/me/certificates', { signal });
}

export function filterTutorCertificates(status?: TutorCertificate['status'], signal?: AbortSignal) {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return get<TutorCertificate[]>(`/api/v1/tutors/me/certificates${query}`, { signal });
}

export function getTutorCertificate(id: string) {
  return get<TutorCertificate>(`/api/v1/tutors/me/certificates/${id}`);
}

export function uploadCertificateFile(file: File) {
  const body = new FormData();
  body.append('file', file);
  return post<{ certificateUrl: string }>('/api/v1/tutors/me/certificates/upload', body);
}

export function createTutorCertificate(values: {
  name: string;
  issuingOrganization?: string;
  description?: string;
  issueDate?: string;
  expiryDate?: string;
  certificateUrl: string;
}) {
  return post<TutorCertificate>('/api/v1/tutors/me/certificates', values);
}

export function updateTutorCertificate(
  id: string,
  values: {
    name: string;
    issuingOrganization?: string;
    description?: string;
    issueDate?: string;
    expiryDate?: string;
    certificateUrl?: string;
  }
) {
  return put<TutorCertificate>(`/api/v1/tutors/me/certificates/${id}`, values);
}

export function deleteTutorCertificate(id: string) {
  return del<void>(`/api/v1/tutors/me/certificates/${id}`);
}

export function getTutorProposals(status?: TutorTeachingProposal['status'], signal?: AbortSignal) {
  const params = new URLSearchParams({ page: '0', size: '100' });
  if (status) params.set('status', status);
  return get<PageResponse<TutorTeachingProposal>>(`/api/v1/tutors/me/tutor-student-requests?${params}`, { signal });
}

export function cancelTutorProposal(id: string) {
  return post(`/api/v1/tutors/me/tutor-student-requests/${id}/cancel`);
}

export function getTutorContracts(signal?: AbortSignal) {
  return get<PageResponse<TutorContract>>('/api/v1/users/me/contracts?page=0&size=100', { signal });
}

export function getTutorContractLessons(contractId: string, signal?: AbortSignal) {
  return get<PageResponse<TutorLesson>>(`/api/v1/tutors/me/contracts/${contractId}/lessons?page=0&size=100`, {
    signal
  });
}
