import { AuthTokens, OAuthProvider } from '../types';
import { STORAGE_KEYS } from '@shared/constants/storage';
import { decodeJwtPayload } from '@shared/utils/jwt';
import { claimsToAuthTokens, KeycloakTokenResponse } from '../utils/keycloakClaims';
import { exchangePkceCode, PkceFlowConfig, startPkceLogin } from '../utils/pkceAuthFlow';
import { REALM_BASE_URL, rpInitiatedLogout } from '../utils/keycloakConfig';

// Deliberately a *separate* client from "gui" (which backs the admin Authorization Code + PKCE
// flow) — kept apart so that client never carries password-grant capability. This whole
// loginWithPassword() function is Option A (direct password grant / ROPC), used here on purpose
// for learning — see AdminLogin's own PKCE flow (adminAuthService.ts) for the recommended
// production pattern. Don't "fix" this into consistency with the admin flow without reading why.
const PASSWORD_CLIENT_ID = 'gui-password-login';

// Social login (startOAuth/handleOAuthCallback) reuses the same "gui" public client the admin
// Authorization Code + PKCE flow uses (adminAuthService.ts) — it's already a standardFlowEnabled
// SPA client with /auth/callback in its redirectUris (docker/keycloak/realm-export.json), so no
// new Keycloak client is needed, just a different redirect_uri/callback route and (via
// startOAuth's extraParams) a kc_idp_hint telling Keycloak's hosted login page to skip straight to
// the given identity provider instead of showing its own username/password form first.
// storageKeyPrefix distinct from adminAuthService's own ("admin") so the two flows can't clobber
// each other's sessionStorage if a user somehow has both in flight in the same browser.
const OAUTH_FLOW_CONFIG: PkceFlowConfig = {
  clientId: 'gui',
  callbackPath: '/auth/callback',
  storageKeyPrefix: 'oauth',
};

export interface AuthService {
  startOAuth(provider: OAuthProvider): Promise<void>;
  handleOAuthCallback(code: string | null, state: string | null, error?: string | null): Promise<void>;
  loginWithPassword(email: string, password: string): Promise<void>;
  refreshAccessToken(): Promise<boolean>;
  storeTokens(tokens: Partial<AuthTokens>): void;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  getIdToken(): string | null;
  getUserUuid(): string | null;
  getUsername(): string | null;
  getEmail(): string | null;
  getRole(): string | null;
  clear(): void;
  logout(): void;
  isAuthenticated(): boolean;
}

export const authService: AuthService = {
  // Authorization Code + PKCE against the "gui" client, with kc_idp_hint telling Keycloak's
  // hosted login page to redirect straight to the given identity provider (Google/Facebook)
  // instead of showing its own username/password form first. Keycloak itself brokers the actual
  // Google/Facebook OAuth dance (docker/keycloak/realm-export.json's identityProviders); this app
  // never talks to either provider directly.
  async startOAuth(provider: OAuthProvider): Promise<void> {
    await startPkceLogin(OAUTH_FLOW_CONFIG, { kc_idp_hint: provider });
  },

  // AuthCallback.tsx's counterpart to adminAuthService.handleCallback — no ADMIN-role gate here,
  // any authenticated account (regular or admin) may sign in via a social provider.
  async handleOAuthCallback(code: string | null, state: string | null, error?: string | null): Promise<void> {
    const authTokens = await exchangePkceCode(OAUTH_FLOW_CONFIG, code, state, error);
    this.storeTokens(authTokens);
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

  // Registered with httpClient (see main.tsx) so a 401 can attempt a silent refresh before
  // logging the user out. A refresh_token grant must be requested from the exact client the
  // token was originally issued to — this app now has two ("gui-password-login" for
  // loginWithPassword, "gui" for the PKCE/social flows above) — so rather than tracking which
  // one alongside the tokens, this decodes the still-stored (possibly expired) access token's own
  // `azp` claim, which Keycloak always stamps with the requesting client_id. Decoding an expired
  // JWT's payload is safe; only signature verification would care about expiry, and we never
  // verify the signature client-side anyway (see decodeJwtPayload's own comment).
  async refreshAccessToken(): Promise<boolean> {
    const refreshToken = this.getRefreshToken();
    const accessToken = this.getAccessToken();
    if (!refreshToken || !accessToken) return false;

    let clientId: string;
    try {
      clientId = decodeJwtPayload<{ azp?: string }>(accessToken).azp ?? OAUTH_FLOW_CONFIG.clientId;
    } catch {
      return false;
    }

    try {
      const response = await fetch(`${REALM_BASE_URL}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: clientId,
          refresh_token: refreshToken,
        }),
      });
      if (!response.ok) return false;

      const tokens: KeycloakTokenResponse = await response.json();
      this.storeTokens(claimsToAuthTokens(tokens));
      return true;
    } catch {
      return false;
    }
  },

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

  getRole(): string | null {
    return localStorage.getItem(STORAGE_KEYS.role);
  },

  clear(): void {
    Object.values(STORAGE_KEYS).forEach((k) => localStorage.removeItem(k));
  },

  // RP-initiated logout (see keycloakConfig.rpInitiatedLogout's own Javadoc) — replacing the old
  // dead POST /api/v1/auth/logout. Shared with adminAuthService.logout(), which used to duplicate
  // this near verbatim.
  logout(): void {
    const idToken = this.getIdToken();
    this.clear();
    rpInitiatedLogout(idToken, '/login');
  },

  // Validates the JWT's actual expiry (`exp` claim) rather than just checking a token is present —
  // an expired-but-still-stored token should not count as authenticated.
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
