import { get, post } from './httpClient';

export type StudyingRequestStatus =
  'DRAFT' | 'PENDING_REVIEW' | 'OPEN' | 'MATCHED' | 'CLOSED' | 'CANCELLED' | 'REJECTED';
export type LearningMode = 'ONLINE' | 'OFFLINE';

export interface StudyingRequestSearchFilters {
  status?: StudyingRequestStatus;
  subjectId?: string;
  gradeId?: string;
  districtId?: string;
  learningMode?: LearningMode;
  page?: number;
  size?: number;
}

export interface StudyingRequestSearchResponse {
  id: string;
  subjectId: string | null;
  subjectName: string | null;
  gradeId: string;
  gradeName: string;
  districtId: string | null;
  districtName: string | null;
  title: string | null;
  description: string | null;
  learningGoals: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  learningMode: LearningMode;
  preferredSchedule: string | null;
  status: StudyingRequestStatus;
  availabilities: { dayOfWeek: number; startTime: string; endTime: string }[];
  studentFirstName: string | null;
  studentLastName: string | null;
  createdAt: string;
  note: string | null;
  detailAddress: string | null;
  provinceName: string | null;
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

export interface TutorStudyingRequestProposal {
  gradeId: string;
  proposedPrice: number;
  teachingMode: LearningMode;
  preferredSchedule?: string;
  preferredProvinceId?: string | null;
  preferredDistrictId?: string | null;
  message?: string;
}

export function getStudyingRequests(filters: StudyingRequestSearchFilters = {}, signal?: AbortSignal) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params.set(key, String(value));
  }
  const query = params.size ? `?${params.toString()}` : '';
  return get<PageResponse<StudyingRequestSearchResponse>>(`/api/v1/studying-requests${query}`, { signal });
}

export function getStudyingRequest(id: string, signal?: AbortSignal) {
  return get<StudyingRequestSearchResponse>(`/api/v1/studying-requests/${encodeURIComponent(id)}`, { signal });
}

export function submitTutorStudyingRequestProposal(id: string, proposal: TutorStudyingRequestProposal) {
  return post(`/api/v1/tutors/me/studying-requests/${encodeURIComponent(id)}/tutor-student-requests`, proposal);
}
