import type { AuthTokenResponse } from '../types/auth';

const STORAGE_KEY = 'ptutor.auth';
const AUTH_CHANGED_EVENT = 'ptutor:auth-changed';

export function getStoredTokens(): AuthTokenResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as AuthTokenResponse;
  } catch {
    clearStoredTokens();
    return null;
  }
}

export function storeTokens(tokens: AuthTokenResponse): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function clearStoredTokens(): void {
  localStorage.removeItem(STORAGE_KEY);
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export { AUTH_CHANGED_EVENT };
