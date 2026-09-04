import React, { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { Box, Divider, InputAdornment, Link, Stack, TextField, Typography } from '@mui/material';
import EmailIcon from '@mui/icons-material/Email';
import { authService } from '../services/authService';
import { OAuthProvider } from '../types';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import SubmitButton from '@shared/components/SubmitButton';
import { isValidEmail } from '@shared/utils/validation';
import { useCart } from '@ecommerce/context/CartContext';
import AuthCard from '../components/AuthCard';
import SocialLoginButtons from '../components/SocialLoginButtons';
import PasswordField from '../components/PasswordField';

interface LoginFormErrors {
  email?: string;
  password?: string;
}

export default function Login(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const navigate = useNavigate();
  const { refresh: refreshCart } = useCart();
  const [searchParams, setSearchParams] = useSearchParams();
  const { loading, guard } = useSubmitGuard();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<LoginFormErrors>({});

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
    const newErrors: LoginFormErrors = {};

    if (!email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!isValidEmail(email)) {
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
    <AuthCard>
      <Typography variant="h5" fontWeight={700} textAlign="center" gutterBottom>
        Welcome Back
      </Typography>
      <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mb: 3 }}>
        Sign in to continue to Dev Knowledge Platform
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

          <PasswordField
            label="Password"
            value={password}
            onChange={setPassword}
            error={!!errors.password}
            helperText={errors.password}
          />

          <SubmitButton type="submit" size="large" fullWidth saving={loading} label="Sign In" />
        </Stack>
      </Box>

      {/* Divider */}
      <Divider sx={{ my: 3 }}>
        <Typography variant="body2" color="text.secondary">
          or continue with
        </Typography>
      </Divider>

      <SocialLoginButtons onSelect={loginWith} />

      {/* Sign Up Link */}
      <Box sx={{ mt: 3, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Don't have an account?{' '}
          <Link component={RouterLink} to="/signup" underline="hover" fontWeight="medium">
            Sign Up
          </Link>
        </Typography>
      </Box>
    </AuthCard>
  );
}
