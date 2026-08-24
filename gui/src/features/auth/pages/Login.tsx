import React, { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  TextField,
  Divider,
  Link,
  InputAdornment,
  IconButton,
  CircularProgress,
} from '@mui/material';
import GoogleIcon from '@mui/icons-material/Google';
import FacebookIcon from '@mui/icons-material/Facebook';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import { authService } from '../services/authService';
import { OAuthProvider } from '../types';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { PROVIDER_COLORS } from '@shared/constants/colors';
import { useCart } from '@ecommerce/context/CartContext';

export default function Login(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { refresh: refreshCart } = useCart();
  const [searchParams, setSearchParams] = useSearchParams();
  const { loading, guard } = useSubmitGuard();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});

  // Landed here from identity-service's sendVerifyEmail redirect (?emailVerified=true) — a
  // one-time confirmation toast, since Keycloak's own verification flow gives no feedback of its
  // own once it redirects back into this app. Strip the param so refreshing doesn't re-show it.
  useEffect(() => {
    if (searchParams.get('emailVerified') === 'true') {
      showSuccess('Your email has been verified! You can now sign in.');
      setSearchParams(params => {
        params.delete('emailVerified');
        return params;
      }, { replace: true });
    }
  }, [searchParams, setSearchParams, showSuccess]);

  const loginWith = (provider: OAuthProvider): void => {
    authService.startOAuth(provider);
  };

  const validateForm = (): boolean => {
    const newErrors: { email?: string; password?: string } = {};
    
    if (!email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Please enter a valid email';
    }
    
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;
    guard(async () => {
      try {
        await authService.loginWithPassword(email, password);
        // CartProvider's own initial fetch already ran (unauthenticated, so it no-opped) before
        // this login happened and won't re-run on its own — refresh explicitly so the NavBar's
        // cart badge reflects any items from a previous session right away.
        refreshCart();
        navigate('/dashboard', { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Login failed. Please try again.';
        showError(message);
      }
    });
  };

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 420 }}>
        <Typography variant="h5" fontWeight="bold" textAlign="center" gutterBottom>
          Welcome Back
        </Typography>
        <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mb: 3 }}>
          Sign in to continue to Duck Chat
        </Typography>

        {/* Email/Password Form */}
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            <TextField
              fullWidth
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={!!errors.email}
              helperText={errors.email}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailIcon color="action" />
                  </InputAdornment>
                ),
              }}
            />
            
            <TextField
              fullWidth
              label="Password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={!!errors.password}
              helperText={errors.password}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                      size="small"
                    >
                      {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign In'}
            </Button>
          </Stack>
        </Box>

        {/* Divider */}
        <Divider sx={{ my: 3 }}>
          <Typography variant="body2" color="text.secondary">
            or continue with
          </Typography>
        </Divider>

        {/* OAuth Buttons */}
        <Stack spacing={2}>
          <Button
            variant="outlined"
            size="large"
            fullWidth
            startIcon={<GoogleIcon />}
            onClick={() => loginWith('google')}
            sx={{
              borderColor: PROVIDER_COLORS.google.main,
              color: PROVIDER_COLORS.google.main,
              '&:hover': {
                borderColor: PROVIDER_COLORS.google.hover,
                backgroundColor: PROVIDER_COLORS.google.hoverBg,
              },
            }}
          >
            Continue with Google
          </Button>

          <Button
            variant="outlined"
            size="large"
            fullWidth
            startIcon={<FacebookIcon />}
            onClick={() => loginWith('facebook')}
            sx={{
              borderColor: PROVIDER_COLORS.facebook.main,
              color: PROVIDER_COLORS.facebook.main,
              '&:hover': {
                borderColor: PROVIDER_COLORS.facebook.hover,
                backgroundColor: PROVIDER_COLORS.facebook.hoverBg,
              },
            }}
          >
            Continue with Facebook
          </Button>
        </Stack>

        {/* Sign Up Link */}
        <Box sx={{ mt: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Don't have an account?{' '}
            <Link component={RouterLink} to="/signup" underline="hover" fontWeight="medium">
              Sign Up
            </Link>
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
}
