import type { ApiResponse } from '../types/api';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers ?? {})
    }
  });
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;

  if (!response.ok || !payload?.success) {
    throw new ApiError(payload?.message ?? 'Không thể tải dữ liệu. Vui lòng thử lại.', response.status, payload?.code);
  }

  return payload.data;
}

export function get<T>(path: string, options: RequestInit = {}): Promise<T> {
  return request<T>(path, { ...options, method: 'GET' });
}

export function post<T>(path: string, body: unknown, options: RequestInit = {}): Promise<T> {
  return request<T>(path, {
    ...options,
    method: 'POST',
    body: JSON.stringify(body)
  });
}
