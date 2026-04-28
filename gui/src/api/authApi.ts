import { AuthTokens } from '../types';
import { httpClient } from './httpClient';

/**
 * Auth API Interface
 * 
 * Handles authentication-related API calls:
 * - OAuth2 state token exchange
 * - Token refresh
 * - Login/Register
 */
interface AuthApi {
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens>;
  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }>;
  login(email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens>;
  register(username: string, email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens>;
}

/**
 * Auth API Implementation
 */
export const authApi: AuthApi = {
  /**
   * Exchange OAuth2 state token for JWT tokens
   * Called after OAuth2 callback to retrieve actual tokens
   * 
   * @param stateToken The state token received from OAuth2 redirect
   * @param showError Optional error notification handler
   * @returns JWT tokens and user info
   */
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/exchange-state',
      { state: stateToken },
      showError
    );
  },

  /**
   * Refresh access token using refresh token
   * 
   * @param refreshToken Current refresh token
   * @param showError Optional error notification handler
   * @returns New access token
   */
  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }> {
    return httpClient.post<{ accessToken: string }>(
      '/api/v1/auth/refresh',
      { refreshToken },
      showError
    );
  },

  /**
   * Login with email and password
   * TODO: Implement when backend endpoint is ready
   */
  login(email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/login',
      { email, password },
      showError
    );
  },

  /**
   * Register new user
   * TODO: Implement when backend endpoint is ready
   */
  register(username: string, email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/register',
      { username, email, password },
      showError
    );
  },
};
