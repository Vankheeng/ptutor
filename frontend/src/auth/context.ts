import { createContext } from 'react';
import type { AuthProfile, AuthUser, LoginRequest } from '../types/auth';

export interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<AuthUser>;
  updateProfile: (profile: AuthProfile) => void;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
