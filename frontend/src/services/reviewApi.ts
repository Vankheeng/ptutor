import { get, post } from './httpClient';
import type { PublicReview } from '../types/api';

export interface ContractReview {
  id: string;
  contractId: string;
  tutorId: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface ContractReviewInput {
  rating: number;
  comment: string | null;
}

export interface TutorRatingDistribution {
  rating: number;
  count: number;
  percentage: number;
}

export interface TutorReviewOverview {
  averageRating: number;
  totalReviews: number;
  commentCount: number;
  distribution: TutorRatingDistribution[];
  comments: {
    content: PublicReview[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
  };
}

export function getReviews(limit: number, signal?: AbortSignal): Promise<PublicReview[]> {
  return get<PublicReview[]>(`/api/v1/reviews?limit=${limit}`, { signal });
}

export function getTutorReviewOverview(
  tutorId: string,
  page = 0,
  size = 5,
  signal?: AbortSignal
): Promise<TutorReviewOverview> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return get<TutorReviewOverview>(`/api/v1/reviews/tutors/${encodeURIComponent(tutorId)}?${params}`, { signal });
}

export function createContractReview(contractId: string, input: ContractReviewInput): Promise<ContractReview> {
  return post<ContractReview>(`/api/v1/students/me/contracts/${encodeURIComponent(contractId)}/review`, input);
}
