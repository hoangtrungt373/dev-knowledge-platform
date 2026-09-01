import { adminAuthService } from '../services/adminAuthService';
import { useOAuthCallback } from '../hooks/useOAuthCallback';
import OAuthCallbackStatus from '../components/OAuthCallbackStatus';

/**
 * Admin login's Authorization Code + PKCE callback — Keycloak redirects here with
 * ?code=...&state=... (or ?error=...) after the admin authenticates on Keycloak's hosted page.
 * Shares its actual exchange/redirect flow with AuthCallback.tsx via useOAuthCallback (see that
 * hook's own Javadoc) — the only differences here are the ADMIN-role-gated exchange and labels.
 */
export default function AdminAuthCallback(): JSX.Element {
  const { error } = useOAuthCallback({
    exchange: async (code, state, errorParam) => {
      await adminAuthService.handleCallback(code, state, errorParam);
    },
    successPath: '/admin/dashboard',
    errorPath: '/admin/login',
    fallbackErrorMessage: 'Admin login failed',
  });

  return (
    <OAuthCallbackStatus
      error={error}
      errorTitle="Admin Login Failed"
      loadingLabel="Completing admin sign-in..."
      redirectingLabel="Redirecting to admin login..."
    />
  );
}
