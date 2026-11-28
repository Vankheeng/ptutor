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

export function getReviews(limit: number, signal?: AbortSignal): Promise<PublicReview[]> {
  return get<PublicReview[]>(`/api/v1/reviews?limit=${limit}`, { signal });
}

export function createContractReview(contractId: string, input: ContractReviewInput): Promise<ContractReview> {
  return post<ContractReview>(`/api/v1/students/me/contracts/${encodeURIComponent(contractId)}/review`, input);
}
