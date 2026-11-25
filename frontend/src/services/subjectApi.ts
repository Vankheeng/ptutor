import { get } from './httpClient';
import type { Subject, SubjectSort } from '../types/api';

export function getSubjects(sort: SubjectSort = 'name', signal?: AbortSignal): Promise<Subject[]> {
  return get<Subject[]>(`/api/v1/subjects?sort=${sort}`, { signal });
}
