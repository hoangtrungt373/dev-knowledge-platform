import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Paper, Typography, Button, CircularProgress, Alert } from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { adminAuthService } from '../services/adminAuthService';
import { authService } from '../services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';

export default function AdminLogin(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [redirecting, setRedirecting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  // Redirecting during render (calling navigate() in the component body, not an effect) triggers
  // "Cannot update a component while rendering a different component" — a real, if usually
  // harmless-looking, React anti-pattern. GuestRoute (Login.tsx's own equivalent guard) avoids it
  // by returning a <Navigate> element instead; this page can't do the same since it always
  // renders its own form, so an effect is the correct fix here instead.
  //
  // Deliberately not GuestRoute itself: this page's two "already signed in" destinations differ
  // by role (an admin belongs at /admin/dashboard, a regular user at /dashboard), and GuestRoute
  // only supports one fixed redirect target for any authenticated visitor — using it here with a
  // single target would either send a non-admin into a redirect loop (PrivateRoute's own
  // requireRole="ADMIN" check on /admin/dashboard would just bounce them straight back here) or
  // send an admin to the wrong dashboard, depending on which target was picked.
  useEffect(() => {
    if (adminAuthService.isAuthenticated()) {
      navigate('/admin/dashboard', { replace: true });
    } else if (authService.isAuthenticated()) {
      // Logged in, but not as an admin — this page's own form has nothing to offer them either;
      // same "don't show a login page to someone already logged in" reasoning GuestRoute applies
      // to /login, just resolved to their own (non-admin) home instead.
      navigate('/dashboard', { replace: true });
    }
  }, [navigate]);

  const handleSignIn = async () => {
    setLoginError(null);
    setRedirecting(true);
    try {
      await adminAuthService.startLogin();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not start login. Please try again.';
      setRedirecting(false);
      setLoginError(message);
      showError(message);
    }
  };

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 420 }}>
        <Box display="flex" justifyContent="center" sx={{ mb: 2 }}>
          <AdminPanelSettingsIcon sx={{ fontSize: 48, color: 'primary.main' }} />
        </Box>

        <Typography variant="h5" fontWeight="bold" textAlign="center" gutterBottom>
          Admin Login
        </Typography>
        <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mb: 3 }}>
          You'll be redirected to sign in securely via Keycloak
        </Typography>

        {loginError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {loginError}
          </Alert>
        )}

        <Button
          variant="contained"
          fullWidth
          disabled={redirecting}
          onClick={handleSignIn}
        >
          {redirecting ? <CircularProgress size={24} color="inherit" /> : 'Sign In with Keycloak'}
        </Button>
      </Paper>
    </Box>
  );
}
