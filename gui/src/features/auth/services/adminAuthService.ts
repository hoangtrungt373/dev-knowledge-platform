import { authService } from './authService';
import { generateCodeChallenge, generateCodeVerifier, generateState } from '../utils/pkce';
import { claimsToAuthTokens, KeycloakTokenResponse } from '../utils/keycloakClaims';

export interface AdminUser {
  userUuid: string;
  username: string;
  email: string;
}

export interface AdminAuthService {
  startLogin(): void;
  handleCallback(code: string | null, state: string | null, error?: string | null): Promise<boolean>;
  logout(): void;
  isAuthenticated(): boolean;
  getToken(): string | null;
  getAdminUser(): AdminUser | null;
}

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'dev-knowledge-platform';
const REALM_BASE_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;
const CLIENT_ID = 'gui';
const CALLBACK_PATH = '/admin/auth/callback';

// sessionStorage, not localStorage — this is a one-shot secret only needed across the redirect
// round trip to Keycloak's hosted login page, never read again after handleCallback runs.
const PKCE_VERIFIER_KEY = 'admin_pkce_verifier';
const PKCE_STATE_KEY = 'admin_pkce_state';

export const adminAuthService: AdminAuthService = {
  // Authorization Code + PKCE, redirecting the browser directly to Keycloak's hosted login page —
  // the "gui" client is a public SPA client (no secret), matching docker/keycloak/realm-export.json.
  async startLogin(): Promise<void> {
    const verifier = generateCodeVerifier();
    const challenge = await generateCodeChallenge(verifier);
    const state = generateState();

    sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
    sessionStorage.setItem(PKCE_STATE_KEY, state);

    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: 'code',
      scope: 'openid',
      redirect_uri: `${window.location.origin}${CALLBACK_PATH}`,
      code_challenge: challenge,
      code_challenge_method: 'S256',
      state,
    });

    window.location.href = `${REALM_BASE_URL}/auth?${params.toString()}`;
  },

  async handleCallback(code: string | null, state: string | null, error?: string | null): Promise<boolean> {
    const storedState = sessionStorage.getItem(PKCE_STATE_KEY);
    const storedVerifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(PKCE_STATE_KEY);
    sessionStorage.removeItem(PKCE_VERIFIER_KEY);

    if (error) {
      throw new Error(`Keycloak login failed: ${error}`);
    }
    if (!code) {
      throw new Error('Missing authorization code');
    }
    // Guards against authorization-code injection / login CSRF — the state value returned by
    // Keycloak must match the one we generated before redirecting.
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
        client_id: CLIENT_ID,
        code,
        redirect_uri: `${window.location.origin}${CALLBACK_PATH}`,
        code_verifier: storedVerifier,
      }),
    });

    if (!response.ok) {
      throw new Error('Failed to exchange authorization code for tokens');
    }

    const tokens: KeycloakTokenResponse = await response.json();
    const authTokens = claimsToAuthTokens(tokens);

    if (authTokens.role !== 'ADMIN') {
      throw new Error('Access denied: admin account required');
    }

    authService.storeTokens(authTokens);
    return true;
  },

  // RP-initiated logout — redirects to Keycloak's own end-session endpoint so the browser's
  // Keycloak SSO cookie is actually cleared, not just this app's local tokens.
  logout(): void {
    const idToken = authService.getIdToken();
    authService.clear();

    const params = new URLSearchParams({
      post_logout_redirect_uri: `${window.location.origin}/admin/login`,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    window.location.href = `${REALM_BASE_URL}/logout?${params.toString()}`;
  },

  isAuthenticated(): boolean {
    return authService.isAuthenticated() && authService.getRole() === 'ADMIN';
  },

  getToken(): string | null {
    return authService.getAccessToken();
  },

  getAdminUser(): AdminUser | null {
    const userUuid = authService.getUserUuid();
    const username = authService.getUsername();
    const email = authService.getEmail();
    if (!userUuid || !username || !email) return null;
    return { userUuid, username, email };
  },
};
