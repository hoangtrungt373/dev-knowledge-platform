import { AuthTokens } from '../types';
import { decodeJwtPayload } from '@shared/utils/jwt';

export interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  id_token: string;
}

interface AccessTokenClaims {
  sub: string;
  preferred_username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
}

/**
 * Adapts a raw Keycloak OIDC token response into this app's stable {@link AuthTokens} shape —
 * shared by both the admin (Authorization Code + PKCE) and regular (password grant) login flows,
 * since both need the same "decode access token claims, derive role" step regardless of which
 * grant type produced the tokens.
 */
export function claimsToAuthTokens(tokens: KeycloakTokenResponse): AuthTokens {
  const claims = decodeJwtPayload<AccessTokenClaims>(tokens.access_token);
  const role = claims.realm_access?.roles?.includes('ADMIN') ? 'ADMIN' : 'USER';

  return {
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    idToken: tokens.id_token,
    userUuid: claims.sub,
    username: claims.preferred_username ?? claims.sub,
    email: claims.email ?? '',
    role,
  };
}
