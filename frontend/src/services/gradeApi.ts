import { get } from './httpClient';
import type { Grade } from '../types/api';

export function getGrades(signal?: AbortSignal): Promise<Grade[]> {
  return get<Grade[]>('/api/v1/grades', { signal });
}
