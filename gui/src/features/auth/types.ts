/**
 * User Types
 * Types related to user data and authentication
 */

/**
 * User entity
 */
export interface User {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  profilePicture?: string;
  provider: string;
  role?: string;
  emailVerified: boolean;
  status: string;
  createdAt: string;
  lastModified?: string;
}

/**
 * Response from the register endpoint
 */
export interface RegisterResponse {
  email: string;
  message: string;
}

/**
 * Auth tokens received after OAuth2 login
 */
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userUuid: string;
  username: string;
  email: string;
  role?: string;
  /** Keycloak's OIDC id_token — needed as id_token_hint for a clean RP-initiated logout. */
  idToken?: string;
}

/**
 * Supported OAuth providers
 */
export type OAuthProvider = 'google' | 'facebook';
