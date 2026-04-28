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
  emailVerified: boolean;
  status: string;
  createdAt: string;
  lastLogin?: string;
}

/**
 * Token response from refresh token endpoint
 */
export interface TokenResponse {
  accessToken: string;
}

/**
 * Refresh token request payload
 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/**
 * Auth tokens received after OAuth2 login
 */
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
  email: string;
  role?: string;
}

/**
 * Supported OAuth providers
 */
export type OAuthProvider = 'google' | 'facebook';
