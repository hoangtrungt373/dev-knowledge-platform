import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Paper, Typography, Button, CircularProgress, Alert } from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { adminAuthService } from '../services/adminAuthService';
import { useNotification } from '@shared/contexts/NotificationContext';

export default function AdminLogin(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [redirecting, setRedirecting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  if (adminAuthService.isAuthenticated()) {
    navigate('/admin/dashboard', { replace: true });
  }

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
