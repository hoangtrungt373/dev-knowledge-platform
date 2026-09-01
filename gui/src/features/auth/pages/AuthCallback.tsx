import { authService } from '../services/authService';
import { useCart } from '@ecommerce/context/CartContext';
import { useOAuthCallback } from '../hooks/useOAuthCallback';
import OAuthCallbackStatus from '../components/OAuthCallbackStatus';

/**
 * Social login's Authorization Code + PKCE callback — Keycloak redirects here with
 * ?code=...&state=... (or ?error=...) after the user authenticates via Google/Facebook (brokered
 * by Keycloak — see authService.startOAuth). Shares its actual exchange/redirect flow with
 * AdminAuthCallback.tsx via useOAuthCallback (the two used to duplicate ~90% of this file) — the
 * only differences here are the ADMIN-role-free exchange, the post-login cart refresh, and labels.
 */
export default function AuthCallback(): JSX.Element {
  const { refresh: refreshCart } = useCart();

  const { error } = useOAuthCallback({
    exchange: async (code, state, errorParam) => {
      await authService.handleOAuthCallback(code, state, errorParam);
      // CartProvider's own initial fetch already ran (unauthenticated, so it no-opped) before this
      // login happened and won't re-run on its own — refresh explicitly so the NavBar's cart badge
      // reflects any items from a previous session right away.
      refreshCart();
    },
    successPath: '/dashboard',
    errorPath: '/login',
    fallbackErrorMessage: 'Login failed',
  });

  return (
    <OAuthCallbackStatus
      error={error}
      errorTitle="Login Failed"
      loadingLabel="Completing sign-in..."
      redirectingLabel="Redirecting to login page..."
    />
  );
}
