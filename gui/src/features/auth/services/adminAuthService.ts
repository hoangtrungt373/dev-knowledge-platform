import { authService } from './authService';
import { exchangePkceCode, PkceFlowConfig, startPkceLogin } from '../utils/pkceAuthFlow';
import { rpInitiatedLogout } from '../utils/keycloakConfig';

export interface AdminUser {
  userUuid: string;
  username: string;
  email: string;
}

export interface AdminAuthService {
  startLogin(): void;
  handleCallback(code: string | null, state: string | null, error?: string | null): Promise<boolean>;
  logout(): void;
  isAuthenticated(): boolean;
  getToken(): string | null;
  getAdminUser(): AdminUser | null;
}

// The "gui" public SPA client (no secret), matching docker/keycloak/realm-export.json.
// storageKeyPrefix distinct from authService's own regular/social login flow's ("oauth") so the
// two can't clobber each other's sessionStorage if somehow both are in flight in one browser.
const ADMIN_FLOW_CONFIG: PkceFlowConfig = {
  clientId: 'gui',
  callbackPath: '/admin/auth/callback',
  storageKeyPrefix: 'admin',
};

export const adminAuthService: AdminAuthService = {
  // Authorization Code + PKCE, redirecting the browser directly to Keycloak's hosted login page.
  async startLogin(): Promise<void> {
    await startPkceLogin(ADMIN_FLOW_CONFIG);
  },

  async handleCallback(code: string | null, state: string | null, error?: string | null): Promise<boolean> {
    const authTokens = await exchangePkceCode(ADMIN_FLOW_CONFIG, code, state, error);

    if (authTokens.role !== 'ADMIN') {
      throw new Error('Access denied: admin account required');
    }

    authService.storeTokens(authTokens);
    return true;
  },

  // RP-initiated logout — see keycloakConfig.rpInitiatedLogout's own Javadoc.
  logout(): void {
    const idToken = authService.getIdToken();
    authService.clear();
    rpInitiatedLogout(idToken, '/admin/login');
  },

  isAuthenticated(): boolean {
    return authService.isAuthenticated() && authService.getRole() === 'ADMIN';
  },

  getToken(): string | null {
    return authService.getAccessToken();
  },

  getAdminUser(): AdminUser | null {
    const userUuid = authService.getUserUuid();
    const username = authService.getUsername();
    const email = authService.getEmail();
    if (!userUuid || !username || !email) return null;
    return { userUuid, username, email };
  },
};
