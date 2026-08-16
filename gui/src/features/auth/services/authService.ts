import { AuthTokens, OAuthProvider } from '../types';
import { STORAGE_KEYS } from '@shared/constants/storage';
import { decodeJwtPayload } from '@shared/utils/jwt';
import { claimsToAuthTokens, KeycloakTokenResponse } from '../utils/keycloakClaims';

// gateway (8080) — see @shared/api/httpClient.ts's own comment on this same default.
const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'dev-knowledge-platform';
const REALM_BASE_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;
// Deliberately a *separate* client from "gui" (which backs the admin Authorization Code + PKCE
// flow) — kept apart so that client never carries password-grant capability. This whole
// loginWithPassword() function is Option A (direct password grant / ROPC), used here on purpose
// for learning — see AdminLogin's own PKCE flow (adminAuthService.ts) for the recommended
// production pattern. Don't "fix" this into consistency with the admin flow without reading why.
const PASSWORD_CLIENT_ID = 'gui-password-login';

export interface AuthService {
  startOAuth(provider: OAuthProvider): void;
  loginWithPassword(email: string, password: string): Promise<void>;
  storeTokens(tokens: Partial<AuthTokens>): void;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  getIdToken(): string | null;
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

  // Option A (direct password grant / ROPC) — POSTs the password straight to Keycloak's token
  // endpoint. See the PASSWORD_CLIENT_ID comment above for why this exists deliberately.
  async loginWithPassword(email: string, password: string): Promise<void> {
    const response = await fetch(`${REALM_BASE_URL}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: PASSWORD_CLIENT_ID,
        username: email,
        password,
        scope: 'openid',
      }),
    });

    if (!response.ok) {
      throw new Error('Invalid email or password');
    }

    const tokens: KeycloakTokenResponse = await response.json();
    this.storeTokens(claimsToAuthTokens(tokens));
  },

  // Fix 8: store role alongside other tokens
  storeTokens(tokens: Partial<AuthTokens>): void {
    if (tokens.accessToken) localStorage.setItem(STORAGE_KEYS.accessToken, tokens.accessToken);
    if (tokens.refreshToken) localStorage.setItem(STORAGE_KEYS.refreshToken, tokens.refreshToken);
    if (tokens.idToken) localStorage.setItem(STORAGE_KEYS.idToken, tokens.idToken);
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

  getIdToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.idToken);
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

  // RP-initiated logout — redirects to Keycloak's own end-session endpoint so the browser's
  // Keycloak SSO cookie is actually cleared, not just this app's local tokens (same fix
  // adminAuthService.logout() already got, replacing the old dead POST /api/v1/auth/logout).
  logout(): void {
    const idToken = this.getIdToken();
    this.clear();

    const params = new URLSearchParams({
      post_logout_redirect_uri: `${window.location.origin}/login`,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    window.location.href = `${REALM_BASE_URL}/logout?${params.toString()}`;
  },

  // Fix 9: validate JWT expiry from the `exp` claim instead of just checking presence
  isAuthenticated(): boolean {
    const token = this.getAccessToken();
    if (!token) return false;
    try {
      const payload = decodeJwtPayload<{ exp?: number }>(token);
      return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  },
};
