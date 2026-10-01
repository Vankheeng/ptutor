import { post } from './httpClient';
import type { AuthTokenResponse, LoginRequest, RegisterRequest, RegisterResponse } from '../types/auth';

export function register(request: RegisterRequest): Promise<RegisterResponse> {
  return post<RegisterResponse>('/api/v1/auth/register', request, { skipAuthRefresh: true });
}

export function login(request: LoginRequest): Promise<AuthTokenResponse> {
  return post<AuthTokenResponse>('/api/v1/auth/login', request, { skipAuthRefresh: true });
}

export function refresh(refreshToken: string): Promise<AuthTokenResponse> {
  return post<AuthTokenResponse>('/api/v1/auth/refresh', { refreshToken }, { skipAuthRefresh: true });
}

export function logout(refreshToken: string): Promise<void> {
  return post<void>('/api/v1/auth/logout', { refreshToken }, { skipAuthRefresh: true });
}
