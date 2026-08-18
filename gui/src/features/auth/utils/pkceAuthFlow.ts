import { generateCodeChallenge, generateCodeVerifier, generateState } from './pkce';
import { claimsToAuthTokens, KeycloakTokenResponse } from './keycloakClaims';
import { AuthTokens } from '../types';

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'dev-knowledge-platform';
const REALM_BASE_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;

/**
 * One Authorization Code + PKCE flow's identity — the pieces that differ between this app's two
 * call sites (`adminAuthService`'s admin login, `authService`'s regular/social login), both
 * against the same public "gui" Keycloak client but with different callback routes.
 */
export interface PkceFlowConfig {
  clientId: string;
  /** This app's own callback route, e.g. `/admin/auth/callback` — combined with `window.location.origin` for `redirect_uri`. */
  callbackPath: string;
  /**
   * sessionStorage key prefix for this flow's one-shot verifier/state — distinct per flow so two
   * PKCE flows in flight in the same browser (unlikely, but cheap to guard) can't clobber each
   * other's session storage.
   */
  storageKeyPrefix: string;
}

function verifierKey(config: PkceFlowConfig): string {
  return `${config.storageKeyPrefix}_pkce_verifier`;
}

function stateKey(config: PkceFlowConfig): string {
  return `${config.storageKeyPrefix}_pkce_state`;
}

/**
 * Redirects the browser to Keycloak's hosted `/auth` endpoint to start an Authorization Code +
 * PKCE flow. `extraParams` covers per-call-site additions (e.g. `authService.startOAuth`'s
 * `kc_idp_hint` for social login) without this shared helper needing to know about them.
 */
export async function startPkceLogin(config: PkceFlowConfig, extraParams?: Record<string, string>): Promise<void> {
  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);
  const state = generateState();

  sessionStorage.setItem(verifierKey(config), verifier);
  sessionStorage.setItem(stateKey(config), state);

  const params = new URLSearchParams({
    client_id: config.clientId,
    response_type: 'code',
    scope: 'openid',
    redirect_uri: `${window.location.origin}${config.callbackPath}`,
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
    ...extraParams,
  });

  window.location.href = `${REALM_BASE_URL}/auth?${params.toString()}`;
}

/**
 * The callback-side counterpart to {@link startPkceLogin} — validates Keycloak's redirect back
 * (`code`/`state`/`error` query params), exchanges the code for tokens, and returns them adapted
 * to this app's {@link AuthTokens} shape. Throws with a user-facing message on any failure; callers
 * decide what to do with the result (e.g. `adminAuthService` additionally gates on `role`).
 */
export async function exchangePkceCode(
  config: PkceFlowConfig,
  code: string | null,
  state: string | null,
  error?: string | null,
): Promise<AuthTokens> {
  const storedState = sessionStorage.getItem(stateKey(config));
  const storedVerifier = sessionStorage.getItem(verifierKey(config));
  sessionStorage.removeItem(stateKey(config));
  sessionStorage.removeItem(verifierKey(config));

  if (error) {
    throw new Error(`Keycloak login failed: ${error}`);
  }
  if (!code) {
    throw new Error('Missing authorization code');
  }
  // Guards against authorization-code injection / login CSRF — the state value returned by
  // Keycloak must match the one generated before redirecting.
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
      client_id: config.clientId,
      code,
      redirect_uri: `${window.location.origin}${config.callbackPath}`,
      code_verifier: storedVerifier,
    }),
  });

  if (!response.ok) {
    throw new Error('Failed to exchange authorization code for tokens');
  }

  const tokens: KeycloakTokenResponse = await response.json();
  return claimsToAuthTokens(tokens);
}
