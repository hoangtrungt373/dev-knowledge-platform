import { AuthTokens, OAuthProvider } from '../types';
import { STORAGE_KEYS } from '@shared/constants/storage';
import { decodeJwtPayload } from '@shared/utils/jwt';
import { claimsToAuthTokens, KeycloakTokenResponse } from '../utils/keycloakClaims';
import { generateCodeChallenge, generateCodeVerifier, generateState } from '../utils/pkce';

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'dev-knowledge-platform';
const REALM_BASE_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;
// Deliberately a *separate* client from "gui" (which backs the admin Authorization Code + PKCE
// flow) — kept apart so that client never carries password-grant capability. This whole
// loginWithPassword() function is Option A (direct password grant / ROPC), used here on purpose
// for learning — see AdminLogin's own PKCE flow (adminAuthService.ts) for the recommended
// production pattern. Don't "fix" this into consistency with the admin flow without reading why.
const PASSWORD_CLIENT_ID = 'gui-password-login';

// Social login (startOAuth/handleOAuthCallback) reuses the same "gui" public client the admin
// Authorization Code + PKCE flow uses (adminAuthService.ts) — it's already a standardFlowEnabled
// SPA client with /auth/callback in its redirectUris (docker/keycloak/realm-export.json), so no
// new Keycloak client is needed, just a different redirect_uri/callback route and a kc_idp_hint
// param telling Keycloak's hosted login page to skip straight to the given identity provider
// instead of showing its own username/password form first.
const OAUTH_CLIENT_ID = 'gui';
const OAUTH_CALLBACK_PATH = '/auth/callback';
// sessionStorage, not localStorage — one-shot secrets only needed across the redirect round trip
// to Keycloak/the upstream IdP, never read again after handleOAuthCallback runs. Distinct keys
// from adminAuthService's own (admin_pkce_*) so the two flows can't clobber each other if a user
// somehow has both in flight in the same browser.
const OAUTH_PKCE_VERIFIER_KEY = 'oauth_pkce_verifier';
const OAUTH_PKCE_STATE_KEY = 'oauth_pkce_state';

export interface AuthService {
  startOAuth(provider: OAuthProvider): Promise<void>;
  handleOAuthCallback(code: string | null, state: string | null, error?: string | null): Promise<void>;
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
  // Authorization Code + PKCE against the "gui" client, with kc_idp_hint telling Keycloak's
  // hosted login page to redirect straight to the given identity provider (Google/Facebook)
  // instead of showing its own username/password form first — same mechanism
  // adminAuthService.startLogin() uses, plus the one extra param. Keycloak itself brokers the
  // actual Google/Facebook OAuth dance (docker/keycloak/realm-export.json's identityProviders);
  // this app never talks to either provider directly.
  async startOAuth(provider: OAuthProvider): Promise<void> {
    const verifier = generateCodeVerifier();
    const challenge = await generateCodeChallenge(verifier);
    const state = generateState();

    sessionStorage.setItem(OAUTH_PKCE_VERIFIER_KEY, verifier);
    sessionStorage.setItem(OAUTH_PKCE_STATE_KEY, state);

    const params = new URLSearchParams({
      client_id: OAUTH_CLIENT_ID,
      response_type: 'code',
      scope: 'openid',
      redirect_uri: `${window.location.origin}${OAUTH_CALLBACK_PATH}`,
      code_challenge: challenge,
      code_challenge_method: 'S256',
      state,
      kc_idp_hint: provider,
    });

    window.location.href = `${REALM_BASE_URL}/auth?${params.toString()}`;
  },

  // AuthCallback.tsx's counterpart to adminAuthService.handleCallback — no ADMIN-role gate here,
  // any authenticated account (regular or admin) may sign in via a social provider.
  async handleOAuthCallback(code: string | null, state: string | null, error?: string | null): Promise<void> {
    const storedState = sessionStorage.getItem(OAUTH_PKCE_STATE_KEY);
    const storedVerifier = sessionStorage.getItem(OAUTH_PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(OAUTH_PKCE_STATE_KEY);
    sessionStorage.removeItem(OAUTH_PKCE_VERIFIER_KEY);

    if (error) {
      throw new Error(`Social login failed: ${error}`);
    }
    if (!code) {
      throw new Error('Missing authorization code');
    }
    // Guards against authorization-code injection / login CSRF — same check
    // adminAuthService.handleCallback performs.
    if (!state || !storedState || state !== storedState) {
      throw new Error('Invalid login state — please try signing in again');
    }
    if (!storedVerifier) {
      throw new Error('Missing PKCE verifier — please try signing in again');
    }

    const response = await fetch(`${REALM_BASE_URL}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: OAUTH_CLIENT_ID,
        code,
        redirect_uri: `${window.location.origin}${OAUTH_CALLBACK_PATH}`,
        code_verifier: storedVerifier,
      }),
    });

    if (!response.ok) {
      throw new Error('Failed to exchange authorization code for tokens');
    }

    const tokens: KeycloakTokenResponse = await response.json();
    this.storeTokens(claimsToAuthTokens(tokens));
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
