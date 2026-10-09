const SESSION_STORAGE_KEY = 'ptutor.admin.session';

interface StoredSession {
  accessToken: string;
  role?: string;
}

interface JwtClaims {
  role?: string;
  exp?: number;
}

export function getAdminAccessToken(): string | undefined {
  const rawSession = sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (!rawSession) return undefined;

  try {
    const session = JSON.parse(rawSession) as StoredSession;
    if (!session.accessToken) return undefined;

    const claims = decodeJwtClaims(session.accessToken);
    const isExpired = typeof claims.exp === 'number' && claims.exp * 1000 <= Date.now();
    if (claims.role !== 'ADMIN' || isExpired) {
      clearAdminSession();
      return undefined;
    }

    return session.accessToken;
  } catch {
    clearAdminSession();
    return undefined;
  }
}

export function clearAdminSession(): void {
  sessionStorage.removeItem(SESSION_STORAGE_KEY);
}

function decodeJwtClaims(token: string): JwtClaims {
  const payload = token.split('.')[1];
  if (!payload) throw new Error('Invalid access token');

  const normalizedPayload = payload
    .replaceAll('-', '+')
    .replaceAll('_', '/')
    .padEnd(Math.ceil(payload.length / 4) * 4, '=');
  return JSON.parse(atob(normalizedPayload)) as JwtClaims;
}
