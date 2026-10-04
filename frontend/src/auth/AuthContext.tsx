import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { clearStoredTokens, getStoredTokens, storeTokens, AUTH_CHANGED_EVENT } from './authStorage';
import { get } from '../services/httpClient';
import { login as loginRequest, logout as logoutRequest } from '../services/authApi';
import type { AuthProfile, AuthTokenResponse, AuthUser, LoginRequest } from '../types/auth';
import { AuthContext } from './context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const userFromTokens = useCallback(
    (tokens: AuthTokenResponse, profile: AuthProfile | null = null): AuthUser => ({
      userId: tokens.userId,
      email: tokens.email,
      role: tokens.role,
      profile
    }),
    []
  );

  const hydrateProfile = useCallback(async (tokens: AuthTokenResponse) => {
    if (tokens.role !== 'STUDENT' && tokens.role !== 'TUTOR') return null;
    try {
      const profile = await get<AuthProfile>(tokens.role === 'STUDENT' ? '/api/v1/students/me' : '/api/v1/tutors/me');
      return profile;
    } catch {
      return null;
    }
  }, []);

  useEffect(() => {
    let active = true;
    const restore = async () => {
      const tokens = getStoredTokens();
      if (!tokens) {
        if (active) setIsLoading(false);
        return;
      }
      if (active) setUser(userFromTokens(tokens));
      const profile = await hydrateProfile(tokens);
      if (active) {
        setUser(userFromTokens(tokens, profile));
        setIsLoading(false);
      }
    };
    void restore();

    const handleAuthChanged = () => {
      const tokens = getStoredTokens();
      if (!tokens) {
        setUser(null);
        setIsLoading(false);
      } else {
        setUser(userFromTokens(tokens));
      }
    };
    window.addEventListener(AUTH_CHANGED_EVENT, handleAuthChanged);
    return () => {
      active = false;
      window.removeEventListener(AUTH_CHANGED_EVENT, handleAuthChanged);
    };
  }, [hydrateProfile, userFromTokens]);

  const login = useCallback(
    async (request: LoginRequest) => {
      const tokens = await loginRequest(request);
      storeTokens(tokens);
      setUser(userFromTokens(tokens));
      const profile = await hydrateProfile(tokens);
      const authenticatedUser = userFromTokens(tokens, profile);
      setUser(authenticatedUser);
      return authenticatedUser;
    },
    [hydrateProfile, userFromTokens]
  );

  const logout = useCallback(async () => {
    const tokens = getStoredTokens();
    clearStoredTokens();
    setUser(null);
    if (tokens?.refreshToken) {
      try {
        await logoutRequest(tokens.refreshToken);
      } catch {
        // The local session is cleared even if the server is unavailable.
      }
    }
  }, []);

  const value = useMemo(
    () => ({ user, isLoading, isAuthenticated: user !== null, login, logout }),
    [isLoading, login, logout, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
