import { get } from './httpClient';

export interface RecommendationWeightVersion {
  version: number;
  active: boolean;
  sampleCount: number;
  trainedUntil: string | null;
  createdAt: string;
  weights: Record<string, number>;
  evaluation: Record<string, unknown> | null;
}

export interface RecommendationWeightModel {
  model: string;
  features: string[];
  prior: Record<string, number>;
  configuredMode: 'OFF' | 'SHADOW' | 'ACTIVE';
  overrideMode: 'OFF' | 'SHADOW' | 'ACTIVE' | null;
  mode: 'OFF' | 'SHADOW' | 'ACTIVE';
  activeVersion: number | null;
  versions: RecommendationWeightVersion[];
}

export function getRecommendationWeights(signal?: AbortSignal) {
  return get<{ models: RecommendationWeightModel[] }>('/api/v1/admin/recommendations/weights', { signal });
}
