import { authApi } from '../api';

const ADMIN_KEYS = {
  accessToken: 'adminAccessToken',
  refreshToken: 'adminRefreshToken',
  userId: 'adminUserId',
  username: 'adminUsername',
  email: 'adminEmail',
} as const;

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

export const adminAuthService: AdminAuthService = {
  async login(email: string, password: string): Promise<boolean> {
    const tokens = await authApi.login(email, password);
    localStorage.setItem(ADMIN_KEYS.accessToken, tokens.accessToken);
    localStorage.setItem(ADMIN_KEYS.refreshToken, tokens.refreshToken);
    localStorage.setItem(ADMIN_KEYS.userId, tokens.userId);
    localStorage.setItem(ADMIN_KEYS.username, tokens.username);
    localStorage.setItem(ADMIN_KEYS.email, tokens.email);
    return true;
  },

  logout(): void {
    Object.values(ADMIN_KEYS).forEach((k) => localStorage.removeItem(k));
    window.location.href = '/admin/login';
  },

  isAuthenticated(): boolean {
    return !!localStorage.getItem(ADMIN_KEYS.accessToken);
  },

  getToken(): string | null {
    return localStorage.getItem(ADMIN_KEYS.accessToken);
  },

  getAdminUser(): AdminUser | null {
    const userId = localStorage.getItem(ADMIN_KEYS.userId);
    const username = localStorage.getItem(ADMIN_KEYS.username);
    const email = localStorage.getItem(ADMIN_KEYS.email);
    if (!userId || !username || !email) return null;
    return { userId, username, email };
  },
};