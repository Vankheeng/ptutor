import { del, get, post, put } from './httpClient';
import type { Availability, TeachingMode } from '../types/api';

export type ModePreference = 'ONLINE' | 'OFFLINE' | 'ANY';
export type AreaScope = 'DISTRICTS' | 'PROVINCE' | 'ANY';
export type RecommendationDirection = 'STUDENT_TO_TUTOR' | 'TUTOR_TO_STUDENT';
export type TagGroup = 'TOPIC' | 'STYLE' | 'GOAL' | 'SEGMENT';
export type RecommendationAction = 'VIEWED' | 'CLICKED' | 'FAVORITED' | 'CONTACTED' | 'DISMISSED';

export interface AreaFilter {
  scope: AreaScope;
  districtIds?: string[];
  provinceId?: string | null;
}

export interface PriceFilter {
  min?: number | null;
  max?: number | null;
}

/** Filters of a student's search for tutors; every field is optional and missing ones are filled by the server. */
export interface TutorSearchFilters {
  subjectId?: string;
  gradeId?: string;
  learningMode?: ModePreference;
  area?: AreaFilter;
  price?: PriceFilter;
  availabilities?: Availability[];
  styleTagCodes?: string[];
  goalTagCodes?: string[];
  topicTagCodes?: string[];
}

export interface TutorSearchRequest {
  filters?: TutorSearchFilters;
  semanticText?: string;
  queryText?: string;
  useStudyingRequestId?: string;
  savedSearchId?: string;
}

export interface RecommendedTutor {
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  averageRating: number | null;
  totalReviews: number | null;
}

export interface TutorRecommendationItem {
  rank: number;
  teachingRequestId: string;
  tutor: RecommendedTutor;
  title: string | null;
  expectedPrice: number | null;
  teachingMode: TeachingMode;
  districts: string[];
  score: number;
  isExploration: boolean;
  reasons: string[];
  caution: string | null;
}

export interface Relaxation {
  code: string;
  message: string;
  gain: number;
  /** Filter values to overwrite for a one-click retry. */
  apply: Partial<TutorSearchFilters> & { expectedPrice?: number };
}

export interface TutorRecommendationResponse {
  logId: string | null;
  algorithmVersion: string;
  queryContext: unknown;
  /** Required criteria still missing; when not empty there are no items. */
  needsInput: string[];
  lowConfidence: boolean;
  items: TutorRecommendationItem[];
  relaxations: Relaxation[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ParsedQuery {
  filters: TutorSearchFilters;
  semanticText: string | null;
  notes: string[];
  understood: boolean;
}

export interface RecommendationTag {
  id: string;
  group: TagGroup;
  subjectId: string | null;
  code: string;
  name: string;
  description: string | null;
}

/** A new search. The server keeps the ranking under `logId`; read further pages with {@link getTutorPage}. */
export function searchTutors(request: TutorSearchRequest, size = 20, signal?: AbortSignal) {
  return post<TutorRecommendationResponse>(`/api/v1/students/me/tutor-recommendations?page=0&size=${size}`, request, {
    signal
  });
}

export function getTutorPage(logId: string, page: number, size = 20, signal?: AbortSignal) {
  return post<TutorRecommendationResponse>(
    `/api/v1/students/me/tutor-recommendations?logId=${encodeURIComponent(logId)}&page=${page}&size=${size}`,
    undefined,
    { signal }
  );
}

/** Scores again what the user has not seen yet, using what they opened or dismissed so far. */
export function refineTutors(logId: string, seenUpToRank: number) {
  return post<TutorRecommendationResponse>(`/api/v1/recommendations/${encodeURIComponent(logId)}/refine`, {
    seenUpToRank
  });
}

export function parseQuery(text: string, direction: RecommendationDirection) {
  return post<ParsedQuery>('/api/v1/recommendations/parse-query', { text, direction });
}

export function recordEvent(logId: string, candidateId: string, action: RecommendationAction) {
  return post<null>(`/api/v1/recommendations/${encodeURIComponent(logId)}/events`, { action, candidateId });
}

const tagRequests = new Map<string, Promise<RecommendationTag[]>>();

/** Tags the user can pick as filters. Topics are returned only together with their subject. */
export function getRecommendationTags(group: TagGroup, subjectId?: string): Promise<RecommendationTag[]> {
  const key = `${group}:${subjectId ?? ''}`;
  const cached = tagRequests.get(key);
  if (cached) return cached;
  const parameters = new URLSearchParams({ group });
  if (subjectId) parameters.set('subjectId', subjectId);
  const request = get<RecommendationTag[]>(`/api/v1/recommendation-tags?${parameters}`).catch((error: unknown) => {
    tagRequests.delete(key);
    throw error;
  });
  tagRequests.set(key, request);
  return request;
}

// ---- A tutor's search for students (design 05, 5.3; 08, 8.4)

/** Filters of a tutor's search for students; missing ones are filled by the server from the profile. */
export interface StudentSearchFilters {
  subjectId?: string;
  gradeIds?: string[];
  learningMode?: ModePreference;
  area?: AreaFilter;
  expectedPrice?: number | null;
  availabilities?: Availability[];
  targetSegmentCodes?: string[];
}

export interface StudentSearchRequest {
  filters?: StudentSearchFilters;
  useTeachingRequestId?: string;
  savedSearchId?: string;
}

/** What a tutor may see of a student in the list: no full name, contact or exact address. */
export interface RecommendedStudent {
  displayName: string;
  district: string | null;
}

export interface StudentRecommendationItem {
  rank: number;
  studyingRequestId: string;
  student: RecommendedStudent;
  title: string | null;
  learningGoals: string | null;
  priceRange: { min: number | null; max: number | null } | null;
  learningMode: TeachingMode;
  score: number;
  isExploration: boolean;
  reasons: string[];
  caution: string | null;
}

export interface StudentRecommendationResponse {
  logId: string | null;
  algorithmVersion: string;
  queryContext: unknown;
  needsInput: string[];
  lowConfidence: boolean;
  items: StudentRecommendationItem[];
  relaxations: Relaxation[];
  page: number;
  size: number;
  totalElements: number;
}

/** A new search; 403 TUTOR_PROFILE_NOT_VERIFIED while the tutor's profile is not approved. */
export function searchStudents(request: StudentSearchRequest, size = 20, signal?: AbortSignal) {
  return post<StudentRecommendationResponse>(`/api/v1/tutors/me/student-recommendations?page=0&size=${size}`, request, {
    signal
  });
}

export function getStudentPage(logId: string, page: number, size = 20, signal?: AbortSignal) {
  return post<StudentRecommendationResponse>(
    `/api/v1/tutors/me/student-recommendations?logId=${encodeURIComponent(logId)}&page=${page}&size=${size}`,
    undefined,
    { signal }
  );
}

/** Scores again what the tutor has not seen yet, using what they opened or dismissed so far. */
export function refineStudents(logId: string, seenUpToRank: number) {
  return post<StudentRecommendationResponse>(`/api/v1/recommendations/${encodeURIComponent(logId)}/refine`, {
    seenUpToRank
  });
}

// ---- Saved searches (design 05, 5.6; 08, 8.6) and the history behind the suggestions (07, 7.4)

export interface SavedSearch {
  id: string;
  direction: RecommendationDirection;
  name: string | null;
  /** The filters as they were saved: those of the tutor search or of the student search. */
  filters: TutorSearchFilters | StudentSearchFilters;
  semanticText: string | null;
  notifyEnabled: boolean;
  lastNotifiedAt: string | null;
  createdAt: string;
}

export interface SavedSearchCreate {
  direction: RecommendationDirection;
  name?: string;
  filters: TutorSearchFilters | StudentSearchFilters;
  semanticText?: string;
  notifyEnabled?: boolean;
}

export function getSavedSearches(signal?: AbortSignal) {
  return get<SavedSearch[]>('/api/v1/users/me/saved-searches', { signal });
}

/** 409 SAVED_SEARCH_LIMIT_REACHED when 5 are already saved. */
export function createSavedSearch(request: SavedSearchCreate) {
  return post<SavedSearch>('/api/v1/users/me/saved-searches', request);
}

/** Only the name and the notification switch can change; other criteria mean a new saved search. */
export function updateSavedSearch(id: string, changes: { name?: string; notifyEnabled?: boolean }) {
  return put<SavedSearch>(`/api/v1/users/me/saved-searches/${encodeURIComponent(id)}`, changes);
}

export function deleteSavedSearch(id: string) {
  return del<null>(`/api/v1/users/me/saved-searches/${encodeURIComponent(id)}`);
}

/** Forgets what the user opened, liked and dismissed: suggestions stop using their history. */
export function deleteRecommendationHistory() {
  return del<null>('/api/v1/users/me/recommendation-profile');
}
