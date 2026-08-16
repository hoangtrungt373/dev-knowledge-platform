import { AuthTokens, RegisterResponse } from '../types';
import { httpClient } from '@shared/api/httpClient';

interface AuthApi {
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens>;
  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }>;
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<RegisterResponse>;
  verifyOtp(email: string, otp: string, showError?: (message: string) => void): Promise<AuthTokens>;
  resendOtp(email: string, showError?: (message: string) => void): Promise<RegisterResponse>;
}

export const authApi: AuthApi = {
  exchangeStateToken(stateToken: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>('/api/v1/auth/exchange-state', { stateToken }, showError);
  },

  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }> {
    return httpClient.post<{ accessToken: string }>('/api/v1/auth/refresh', { refreshToken }, showError);
  },

  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<RegisterResponse> {
    return httpClient.post<RegisterResponse>('/api/v1/auth/register', { firstName, lastName, email, password }, showError);
  },

  verifyOtp(email: string, otp: string, showError?: (message: string) => void): Promise<AuthTokens> {
    return httpClient.post<AuthTokens>('/api/v1/auth/verify-otp', { email, otp }, showError);
  },

  resendOtp(email: string, showError?: (message: string) => void): Promise<RegisterResponse> {
    return httpClient.post<RegisterResponse>('/api/v1/auth/resend-otp', { email }, showError);
  },
};