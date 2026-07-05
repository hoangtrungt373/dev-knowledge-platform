import { AuthTokens, OAuthProvider } from '../types';
import { STORAGE_KEYS } from '../constants/storage';

const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';

export interface AuthService {
  startOAuth(provider: OAuthProvider): void;
  storeTokens(tokens: Partial<AuthTokens>): void;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  getUserUuid(): string | null;
  getUsername(): string | null;
  getEmail(): string | null;
  getRole(): string | null;   // Fix 8
  clear(): void;
  logout(): void;
  isAuthenticated(): boolean;
}

export const authService: AuthService = {
  startOAuth(provider: OAuthProvider): void {
    window.location.href = `${BACKEND_BASE_URL}/api/v1/auth/oauth2/authorization/${provider}`;
  },

  // Fix 8: store role alongside other tokens
  storeTokens(tokens: Partial<AuthTokens>): void {
    if (tokens.accessToken) localStorage.setItem(STORAGE_KEYS.accessToken, tokens.accessToken);
    if (tokens.refreshToken) localStorage.setItem(STORAGE_KEYS.refreshToken, tokens.refreshToken);
    if (tokens.userUuid) localStorage.setItem(STORAGE_KEYS.userUuid, tokens.userUuid);
    if (tokens.username) localStorage.setItem(STORAGE_KEYS.username, tokens.username);
    if (tokens.email) localStorage.setItem(STORAGE_KEYS.email, tokens.email);
    if (tokens.role) localStorage.setItem(STORAGE_KEYS.role, tokens.role);
  },

  getAccessToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.accessToken);
  },

  getRefreshToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.refreshToken);
  },

  getUserUuid(): string | null {
    return localStorage.getItem(STORAGE_KEYS.userUuid);
  },

  getUsername(): string | null {
    return localStorage.getItem(STORAGE_KEYS.username);
  },

  getEmail(): string | null {
    return localStorage.getItem(STORAGE_KEYS.email);
  },

  // Fix 8
  getRole(): string | null {
    return localStorage.getItem(STORAGE_KEYS.role);
  },

  clear(): void {
    Object.values(STORAGE_KEYS).forEach((k) => localStorage.removeItem(k));
  },

  // Fix 6: call backend logout to blacklist the refresh token before clearing
  logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      // Fire-and-forget — don't block the redirect on network errors
      fetch(`${BACKEND_BASE_URL}/api/v1/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {});
    }
    this.clear();
    window.location.href = '/login';
  },

  // Fix 9: validate JWT expiry from the `exp` claim instead of just checking presence
  isAuthenticated(): boolean {
    const token = this.getAccessToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  },
};
