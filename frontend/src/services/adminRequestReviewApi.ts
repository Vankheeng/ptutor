import { get, patch } from './httpClient';
import type { PageResponse } from './tutorApi';
import type { Availability, Reference, Subject, TeachingMode } from '../types/api';

export type ReviewKind = 'teaching' | 'studying';
export type ReviewStatus = 'PENDING_REVIEW' | 'OPEN' | 'REJECTED';

export interface Reviewer {
  id: string;
  name: string;
}

export interface AdminTeachingRequestSummary {
  id: string;
  tutorId: string;
  tutorName: string;
  tutorEmail: string;
  subjectName: string | null;
  title: string;
  note: string | null;
  expectedPrice: number;
  teachingMode: TeachingMode;
  status: ReviewStatus;
  createdAt: string;
  reviewedAt: string | null;
}

export interface AdminTeachingRequestDetail {
  id: string;
  tutorId: string;
  tutorName: string;
  tutorEmail: string;
  tutorAvatarUrl: string | null;
  subjectId: string | null;
  subjectName: string | null;
  customSubjectName: string | null;
  grades: Reference[];
  districts: Reference[];
  title: string;
  note: string | null;
  quantity: number;
  detailAddress: string | null;
  expectedPrice: number;
  teachingMode: TeachingMode;
  preferredSchedule: string | null;
  description: string | null;
  availabilities: Availability[];
  status: ReviewStatus;
  reviewer: Reviewer | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminStudyingRequestSummary {
  id: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  subjectName: string | null;
  gradeName: string;
  title: string;
  note: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  learningMode: TeachingMode;
  status: ReviewStatus;
  createdAt: string;
  reviewedAt: string | null;
}

export interface AdminStudyingRequestDetail {
  id: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  studentAvatarUrl: string | null;
  subjectId: string | null;
  subjectName: string | null;
  grade: Reference;
  district: Reference | null;
  title: string;
  description: string | null;
  note: string | null;
  detailAddress: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  learningGoals: string | null;
  learningMode: TeachingMode;
  preferredSchedule: string | null;
  availabilities: Availability[];
  status: ReviewStatus;
  reviewer: Reviewer | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export type SubjectResolution =
  { action: 'USE_EXISTING'; subjectId: string } | { action: 'CREATE'; name: string; description?: string };

export interface ReviewQuery {
  status: ReviewStatus;
  keyword?: string;
  page: number;
  size?: number;
}

const basePath: Record<ReviewKind, string> = {
  teaching: '/api/v1/admin/teaching-requests',
  studying: '/api/v1/admin/studying-requests'
};

function listParams({ status, keyword, page, size = 20 }: ReviewQuery) {
  const params = new URLSearchParams({ status, page: String(page), size: String(size) });
  if (keyword) params.set('keyword', keyword);
  return params;
}

export function getAdminTeachingRequests(query: ReviewQuery, signal?: AbortSignal) {
  return get<PageResponse<AdminTeachingRequestSummary>>(`${basePath.teaching}?${listParams(query)}`, { signal });
}

export function getAdminStudyingRequests(query: ReviewQuery, signal?: AbortSignal) {
  return get<PageResponse<AdminStudyingRequestSummary>>(`${basePath.studying}?${listParams(query)}`, { signal });
}

export function getAdminTeachingRequest(id: string, signal?: AbortSignal) {
  return get<AdminTeachingRequestDetail>(`${basePath.teaching}/${id}`, { signal });
}

export function getAdminStudyingRequest(id: string, signal?: AbortSignal) {
  return get<AdminStudyingRequestDetail>(`${basePath.studying}/${id}`, { signal });
}

/** A teaching request whose subject is already in the catalogue must be approved without a resolution. */
export function approveRequest(kind: ReviewKind, id: string, subjectResolution?: SubjectResolution) {
  return patch<unknown>(`${basePath[kind]}/${id}/approve`, subjectResolution ? { subjectResolution } : {});
}

export function rejectRequest(kind: ReviewKind, id: string, rejectionReason: string) {
  return patch<unknown>(`${basePath[kind]}/${id}/reject`, { rejectionReason });
}

/** Includes inactive subjects, which the backend also counts when checking for a duplicate name. */
export function getAdminSubjects(signal?: AbortSignal) {
  return get<Subject[]>('/api/v1/admin/subjects', { signal });
}
