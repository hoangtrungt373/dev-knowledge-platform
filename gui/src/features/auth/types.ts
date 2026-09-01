/**
 * User Types
 * Types related to user data and authentication
 */

/** Mirrors identity-service's UserRole enum. */
export type Role = 'ADMIN' | 'USER';

/** Mirrors identity-service's UserProvider enum. */
export type UserProvider = 'LOCAL' | 'GOOGLE' | 'FACEBOOK';

/** Mirrors identity-service's UserStatus enum. */
export type UserStatus = 'ONLINE' | 'OFFLINE' | 'AWAY' | 'BUSY';

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
  provider: UserProvider;
  role?: Role;
  emailVerified: boolean;
  status: UserStatus;
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
  role?: Role;
  /** Keycloak's OIDC id_token — needed as id_token_hint for a clean RP-initiated logout. */
  idToken?: string;
}

/**
 * Supported OAuth providers
 */
export type OAuthProvider = 'google' | 'facebook';
