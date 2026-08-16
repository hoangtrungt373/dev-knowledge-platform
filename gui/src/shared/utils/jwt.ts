/**
 * Decodes a JWT's payload segment (no signature verification — the token was already issued to us
 * over TLS by Keycloak; this is for reading claims client-side, not for trusting the token's origin).
 */
export function decodeJwtPayload<T = any>(token: string): T {
  const payload = token.split('.')[1];
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(base64));
}
