import type { ApiResponse } from '../types/api';
import type { AuthTokenResponse } from '../types/auth';
import { clearStoredTokens, getStoredTokens, storeTokens } from '../auth/authStorage';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(message: string, status = 0, code = 'REQUEST_FAILED', fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export interface HttpRequestOptions extends RequestInit {
  skipAuthRefresh?: boolean;
}

let refreshPromise: Promise<AuthTokenResponse | null> | null = null;

export async function get<T>(path: string, options: HttpRequestOptions = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'GET' });
}

export async function post<T>(path: string, body?: unknown, options: HttpRequestOptions = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
}

export async function put<T>(path: string, body?: unknown, options: HttpRequestOptions = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) });
}

export async function patch<T>(path: string, body?: unknown, options: HttpRequestOptions = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) });
}

export async function request<T>(path: string, options: HttpRequestOptions = {}, retry = true): Promise<T> {
  const { skipAuthRefresh, ...fetchOptions } = options;
  const storedTokens = getStoredTokens();
  const headers = new Headers(fetchOptions.headers);
  headers.set('Accept', 'application/json');
  if (fetchOptions.body !== undefined) headers.set('Content-Type', 'application/json');
  if (storedTokens?.accessToken) headers.set('Authorization', `Bearer ${storedTokens.accessToken}`);

  const response = await fetch(`${apiBaseUrl}${path}`, { ...fetchOptions, headers });
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;

  const isAuthEndpoint = path.startsWith('/api/v1/auth/');
  if (response.status === 401 && retry && !skipAuthRefresh && !isAuthEndpoint && storedTokens?.refreshToken) {
    const refreshed = await refreshTokensOnce(storedTokens.refreshToken);
    if (refreshed) return request<T>(path, options, false);
    clearStoredTokens();
  }

  if (!response.ok || !payload?.success) {
    throw new ApiError(
      payload?.message ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.',
      response.status,
      payload?.code ?? 'REQUEST_FAILED',
      payload?.errors ?? {}
    );
  }

  return payload.data;
}

async function refreshTokensOnce(refreshToken: string): Promise<AuthTokenResponse | null> {
  if (!refreshPromise) {
    refreshPromise = post<AuthTokenResponse>('/api/v1/auth/refresh', { refreshToken }, { skipAuthRefresh: true })
      .then((tokens) => {
        storeTokens(tokens);
        return tokens;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}
