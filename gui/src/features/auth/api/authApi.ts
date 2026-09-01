import { RegisterResponse } from '../types';
import { httpClient } from '@shared/api/httpClient';

interface AuthApi {
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<RegisterResponse>;
  resendVerificationEmail(showError?: (message: string) => void): Promise<void>;
}

export const authApi: AuthApi = {
  register(firstName: string, lastName: string | undefined, email: string, password: string, showError?: (message: string) => void): Promise<RegisterResponse> {
    return httpClient.post<RegisterResponse>('/api/v1/auth/register', { firstName, lastName, email, password }, showError);
  },

  // Re-sends Keycloak's own "Verify Email" link to the authenticated caller's own account —
  // backs ProfilePage.tsx's email-verification banner. 204 on success; 409 (via showError) if
  // already verified.
  resendVerificationEmail(showError?: (message: string) => void): Promise<void> {
    return httpClient.post<void>('/api/v1/auth/resend-verification-email', undefined, showError);
  },
};