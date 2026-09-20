import { get } from './httpClient';
import type { PublicReview } from '../types/api';

export function getReviews(limit: number, signal?: AbortSignal): Promise<PublicReview[]> {
  return get<PublicReview[]>(`/api/v1/reviews?limit=${limit}`, { signal });
}
