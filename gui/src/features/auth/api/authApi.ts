import { AuthTokens, RegisterResponse } from '../types';
import { httpClient } from '@shared/api/httpClient';

interface AuthApi {
  refreshToken(refreshToken: string, showError?: (message: string) => void): Promise<{ accessToken: string }>;
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<RegisterResponse>;
  verifyOtp(email: string, otp: string, showError?: (message: string) => void): Promise<AuthTokens>;
  resendOtp(email: string, showError?: (message: string) => void): Promise<RegisterResponse>;
  resendVerificationEmail(showError?: (message: string) => void): Promise<void>;
}

export const authApi: AuthApi = {
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

  // Re-sends Keycloak's own "Verify Email" link to the authenticated caller's own account —
  // backs Dashboard.tsx's email-verification banner. 204 on success; 409 (via showError) if
  // already verified.
  resendVerificationEmail(showError?: (message: string) => void): Promise<void> {
    return httpClient.post<void>('/api/v1/auth/resend-verification-email', undefined, showError);
  },
};