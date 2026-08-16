import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import { adminAuthService } from '../services/adminAuthService';
import { useNotification } from '@shared/contexts/NotificationContext';

/**
 * Admin login's Authorization Code + PKCE callback — Keycloak redirects here with
 * ?code=...&state=... (or ?error=...) after the admin authenticates on Keycloak's hosted page.
 */
export default function AdminAuthCallback(): JSX.Element | null {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const { showError, showSuccess } = useNotification();

  useEffect(() => {
    const run = async () => {
      try {
        const code = searchParams.get('code');
        const state = searchParams.get('state');
        const errorParam = searchParams.get('error');

        await adminAuthService.handleCallback(code, state, errorParam);

        showSuccess('Login successful!');
        navigate('/admin/dashboard', { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Admin login failed';
        setError(message);
        showError(message);
        setTimeout(() => {
          navigate('/admin/login', { replace: true });
        }, 2000);
      }
    };

    run();
  }, [navigate, searchParams, showError, showSuccess]);

  if (error) {
    return (
      <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh" sx={{ px: 2 }}>
        <Alert severity="error" sx={{ mb: 2, maxWidth: 500 }}>
          <Typography variant="h6">Admin Login Failed</Typography>
          <Typography variant="body2">{error}</Typography>
        </Alert>
        <Typography variant="body2" color="text.secondary">
          Redirecting to admin login...
        </Typography>
      </Box>
    );
  }

  return (
    <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh">
      <CircularProgress size={60} />
      <Typography variant="body1" sx={{ mt: 3 }}>
        Completing admin sign-in...
      </Typography>
    </Box>
  );
}
