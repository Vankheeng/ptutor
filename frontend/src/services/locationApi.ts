import { get } from './httpClient';
import type { District, Province } from '../types/api';

let provincesPromise: Promise<Province[]> | null = null;
const districtPromises = new Map<string, Promise<District[]>>();

export function getProvinces(): Promise<Province[]> {
  if (!provincesPromise) {
    provincesPromise = get<Province[]>('/api/v1/provinces').catch((error: unknown) => {
      provincesPromise = null;
      throw error;
    });
  }
  return provincesPromise;
}

export function getDistricts(provinceId: string): Promise<District[]> {
  const cachedPromise = districtPromises.get(provinceId);
  if (cachedPromise) return cachedPromise;

  const request = get<District[]>(`/api/v1/districts?provinceId=${encodeURIComponent(provinceId)}`).catch(
    (error: unknown) => {
      districtPromises.delete(provinceId);
      throw error;
    }
  );
  districtPromises.set(provinceId, request);
  return request;
}
