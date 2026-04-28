import { AuthTokens, OAuthProvider } from '../types';

const STORAGE_KEYS = {
  accessToken: 'accessToken',
  refreshToken: 'refreshToken',
  userId: 'userId',
  username: 'username',
  email: 'email'
} as const;

const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';

/**
 * Auth Service
 * 
 * Manages authentication state:
 * - Token storage (localStorage)
 * - OAuth2 flow initiation
 * - Session management (login/logout)
 * 
 * Note: This service handles CLIENT-SIDE auth state.
 * For API calls, use authApi from '../api'
 */
export interface AuthService {
  startOAuth(provider: OAuthProvider): void;
  storeTokens(tokens: Partial<AuthTokens>): void;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  getUserId(): string | null;
  getUsername(): string | null;
  getEmail(): string | null;
  clear(): void;
  logout(): void;
  isAuthenticated(): boolean;
}

export const authService: AuthService = {
  /**
   * Start OAuth2 login flow
   * Redirects user to backend OAuth2 endpoint
   */
  startOAuth(provider: OAuthProvider): void {
    window.location.href = `${BACKEND_BASE_URL}/api/v1/auth/oauth2/authorization/${provider}`;
  },

  /**
   * Store auth tokens in localStorage
   */
  storeTokens(tokens: Partial<AuthTokens>): void {
    if (tokens.accessToken) localStorage.setItem(STORAGE_KEYS.accessToken, tokens.accessToken);
    if (tokens.refreshToken) localStorage.setItem(STORAGE_KEYS.refreshToken, tokens.refreshToken);
    if (tokens.userId) localStorage.setItem(STORAGE_KEYS.userId, tokens.userId);
    if (tokens.username) localStorage.setItem(STORAGE_KEYS.username, tokens.username);
    if (tokens.email) localStorage.setItem(STORAGE_KEYS.email, tokens.email);
  },

  /**
   * Get access token from storage
   */
  getAccessToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.accessToken);
  },

  /**
   * Get refresh token from storage
   */
  getRefreshToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.refreshToken);
  },

  /**
   * Get stored user ID
   */
  getUserId(): string | null {
    return localStorage.getItem(STORAGE_KEYS.userId);
  },

  /**
   * Get stored username
   */
  getUsername(): string | null {
    return localStorage.getItem(STORAGE_KEYS.username);
  },

  /**
   * Get stored email
   */
  getEmail(): string | null {
    return localStorage.getItem(STORAGE_KEYS.email);
  },

  /**
   * Clear all auth data from storage
   */
  clear(): void {
    Object.values(STORAGE_KEYS).forEach((k) => localStorage.removeItem(k));
  },

  /**
   * Logout user and redirect to login page
   */
  logout(): void {
    this.clear();
    window.location.href = '/login';
  },

  /**
   * Check if user is authenticated (has access token)
   */
  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  },
};