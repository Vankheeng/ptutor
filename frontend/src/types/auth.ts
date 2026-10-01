import type { UserRole } from './api';

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer' | string;
  accessTokenExpiresIn: number;
  refreshTokenExpiresIn: number;
  userId: string;
  email: string;
  role: UserRole;
}

export interface AuthProfile {
  firstName?: string | null;
  lastName?: string | null;
  avatarUrl?: string | null;
}

export interface AuthUser {
  userId: string;
  email: string;
  role: UserRole;
  profile: AuthProfile | null;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  role: 'STUDENT' | 'TUTOR';
}

export interface RegisterRequest {
  email: string;
  password: string;
  role: 'STUDENT' | 'TUTOR';
  firstName: string;
  lastName: string;
  phone: string;
  gender: 'MALE' | 'FEMALE' | 'OTHER';
  dateOfBirth: string;
  citizenId: string;
  provinceId: string;
  districtId: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
