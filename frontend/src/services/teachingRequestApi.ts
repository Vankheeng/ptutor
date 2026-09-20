import { get } from './httpClient';
import type { TeachingRequest } from '../types/api';

export interface TeachingRequestFilters {
  subjectId?: string;
  gradeId?: string;
}

export function getTeachingRequests(
  limit: number,
  filters: TeachingRequestFilters = {},
  signal?: AbortSignal
): Promise<TeachingRequest[]> {
  const parameters = new URLSearchParams({ limit: String(limit) });
  if (filters.subjectId) parameters.set('subjectId', filters.subjectId);
  if (filters.gradeId) parameters.set('gradeId', filters.gradeId);
  return get<TeachingRequest[]>(`/api/v1/teaching-requests?${parameters}`, { signal });
}
