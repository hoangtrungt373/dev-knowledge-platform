import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import { authService } from '../services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';

/**
 * Social login's Authorization Code + PKCE callback — Keycloak redirects here with
 * ?code=...&state=... (or ?error=...) after the user authenticates via Google/Facebook (brokered
 * by Keycloak — see authService.startOAuth). Mirrors AdminAuthCallback.tsx's shape, minus the
 * ADMIN-role gate.
 */
export default function AuthCallback(): JSX.Element | null {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const { showError, showSuccess } = useNotification();
  // The authorization code + PKCE verifier are both one-time-use, so this effect isn't idempotent
  // — StrictMode's deliberate dev-mode double-invoke would otherwise run the exchange twice, with
  // the second call failing (code/verifier already consumed) and bouncing back to /login even
  // though the first call already succeeded. Same guard AdminAuthCallback.tsx uses.
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const run = async () => {
      try {
        const code = searchParams.get('code');
        const state = searchParams.get('state');
        const errorParam = searchParams.get('error');

        await authService.handleOAuthCallback(code, state, errorParam);

        showSuccess('Login successful!');
        navigate('/dashboard', { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Login failed';
        setError(message);
        showError(message);
        setTimeout(() => {
          navigate('/login', { replace: true });
        }, 2000);
      }
    };

    run();
  }, [navigate, searchParams, showError, showSuccess]);

  if (error) {
    return (
      <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh" sx={{ px: 2 }}>
        <Alert severity="error" sx={{ mb: 2, maxWidth: 500 }}>
          <Typography variant="h6">Login Failed</Typography>
          <Typography variant="body2">{error}</Typography>
        </Alert>
        <Typography variant="body2" color="text.secondary">
          Redirecting to login page...
        </Typography>
      </Box>
    );
  }

  return (
    <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh">
      <CircularProgress size={60} />
      <Typography variant="body1" sx={{ mt: 3 }}>
        Completing sign-in...
      </Typography>
    </Box>
  );
}

