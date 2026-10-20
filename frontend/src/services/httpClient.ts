import type { ApiResponse } from '../types/api';
import type { AuthTokenResponse } from '../types/auth';
import { clearStoredTokens, getStoredTokens, storeTokens } from '../auth/authStorage';
import { fieldMessage, MESSAGES, messageForStatus } from './errorCodes';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  /** The server's own (English) message, kept for debugging; {@link message} is the Vietnamese one. */
  readonly serverMessage: string | null;

  constructor(
    message: string,
    status = 0,
    code = 'REQUEST_FAILED',
    fieldErrors: Record<string, string> = {},
    serverMessage: string | null = null
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.serverMessage = serverMessage;
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
  return request<T>(path, {
    ...options,
    method: 'POST',
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body)
  });
}

export async function del<T>(path: string, options: HttpRequestOptions = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'DELETE' });
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
  if (fetchOptions.body !== undefined && !(fetchOptions.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
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
    // The server writes English; what reaches the pages is Vietnamese.
    const code = payload?.code ?? 'REQUEST_FAILED';
    const fieldErrors = Object.fromEntries(
      Object.entries(payload?.errors ?? {}).map(([field, message]) => [field, fieldMessage(String(message))])
    );
    throw new ApiError(
      MESSAGES[code] ?? messageForStatus(response.status),
      response.status,
      code,
      fieldErrors,
      payload?.message ?? null
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
