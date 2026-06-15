import { authApi } from '../api';
import { authService } from './authService';

export interface AdminUser {
  userId: string;
  username: string;
  email: string;
}

export interface AdminAuthService {
  login(email: string, password: string): Promise<boolean>;
  logout(): void;
  isAuthenticated(): boolean;
  getToken(): string | null;
  getAdminUser(): AdminUser | null;
}

// Fix 10: delegate all storage to authService so httpClient picks up the same `accessToken` key.
// Previously admin tokens were stored under separate keys (adminAccessToken etc.) which meant
// admin API calls to /api/v1/admin/** were sent without any Authorization header.
export const adminAuthService: AdminAuthService = {
  async login(email: string, password: string): Promise<boolean> {
    const tokens = await authApi.login(email, password);
    if (tokens.role !== 'ADMIN') {
      throw new Error('Access denied: admin account required');
    }
    authService.storeTokens(tokens);
    return true;
  },

  // Fix 6 (admin): blacklist refresh token on backend before clearing
  logout(): void {
    const refreshToken = authService.getRefreshToken();
    const backendUrl = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';
    if (refreshToken) {
      fetch(`${backendUrl}/api/v1/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {});
    }
    authService.clear();
    window.location.href = '/admin/login';
  },

  // Fix 10: admin session is valid only when the stored token belongs to an ADMIN
  isAuthenticated(): boolean {
    return authService.isAuthenticated() && authService.getRole() === 'ADMIN';
  },

  getToken(): string | null {
    return authService.getAccessToken();
  },

  getAdminUser(): AdminUser | null {
    const userId = authService.getUserId();
    const username = authService.getUsername();
    const email = authService.getEmail();
    if (!userId || !username || !email) return null;
    return { userId, username, email };
  },
};
