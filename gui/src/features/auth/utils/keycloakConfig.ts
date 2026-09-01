/**
 * Single source of truth for this app's Keycloak realm coordinates — used to be recomputed
 * identically in three places (pkceAuthFlow.ts, authService.ts, adminAuthService.ts); all three
 * now import from here instead.
 */
export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180';
export const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'dev-knowledge-platform';
export const REALM_BASE_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;

/**
 * RP-initiated logout — redirects the browser to Keycloak's own end-session endpoint so the
 * browser's Keycloak SSO cookie is actually cleared, not just this app's local tokens. Shared by
 * both authService.logout() and adminAuthService.logout(), which used to duplicate this near
 * verbatim, differing only in `redirectPath`.
 */
export function rpInitiatedLogout(idToken: string | null, redirectPath: string): void {
  const params = new URLSearchParams({
    post_logout_redirect_uri: `${window.location.origin}${redirectPath}`,
    ...(idToken ? { id_token_hint: idToken } : {}),
  });
  window.location.href = `${REALM_BASE_URL}/logout?${params.toString()}`;
}
