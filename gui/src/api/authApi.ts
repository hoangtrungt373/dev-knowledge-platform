import { AuthTokens } from '../types';
import { httpClient } from './httpClient';

interface AuthApi {
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens>;
  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }>;
  login(email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens>;
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens>;
}

export const authApi: AuthApi = {
  // Fix 1: field must be `stateToken`, not `state`
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/exchange-state',
      { stateToken },
      showError
    );
  },

  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }> {
    return httpClient.post<{ accessToken: string }>(
      '/api/v1/auth/refresh',
      { refreshToken },
      showError
    );
  },

  login(email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/login',
      { email, password },
      showError
    );
  },

  // Fix 2: backend expects { firstName, lastName, email, password }, not { username, email, password }
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>(
      '/api/v1/auth/register',
      { firstName, lastName, email, password },
      showError
    );
  },
};
