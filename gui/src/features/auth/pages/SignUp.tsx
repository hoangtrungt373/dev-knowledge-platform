import React, { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Box, Divider, InputAdornment, Link, Stack, TextField, Typography } from '@mui/material';
import EmailIcon from '@mui/icons-material/Email';
import PersonIcon from '@mui/icons-material/Person';
import { authService } from '../services/authService';
import { authApi } from '../api/authApi';
import { OAuthProvider } from '../types';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import SubmitButton from '@shared/components/SubmitButton';
import { isValidEmail } from '@shared/utils/validation';
import { useCart } from '@ecommerce/context/CartContext';
import AuthCard from '../components/AuthCard';
import SocialLoginButtons from '../components/SocialLoginButtons';
import PasswordField from '../components/PasswordField';

interface FormErrors {
  firstName?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
}

export default function SignUp(): JSX.Element {
  const { showError } = useNotification();
  const navigate = useNavigate();
  const { loading, guard } = useSubmitGuard();
  const { refresh: refreshCart } = useCart();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});

  const loginWith = (provider: OAuthProvider): void => {
    authService.startOAuth(provider);
  };

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};

    if (!firstName.trim()) {
      newErrors.firstName = 'First name is required';
    } else if (firstName.trim().length > 255) {
      newErrors.firstName = 'First name must be less than 255 characters';
    }

    if (!email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!isValidEmail(email)) {
      newErrors.email = 'Please enter a valid email';
    }

    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    } else if (!/(?=.*[a-z])/.test(password)) {
      newErrors.password = 'Password must contain at least one lowercase letter';
    } else if (!/(?=.*[A-Z])/.test(password)) {
      newErrors.password = 'Password must contain at least one uppercase letter';
    } else if (!/(?=.*\d)/.test(password)) {
      newErrors.password = 'Password must contain at least one number';
    }

    if (!confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your password';
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;
    guard(async () => {
      try {
        // No showError passthrough here — a single catch below handles both calls' errors so
        // failures aren't shown twice.
        await authApi.register(firstName.trim(), lastName.trim() || undefined, email, password);
        await authService.loginWithPassword(email, password);
        refreshCart(); // brand-new account, but keeps this path consistent with Login.tsx's own call
        navigate('/dashboard', { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Registration failed. Please try again.';
        showError(message);
      }
    });
  };

  return (
    <AuthCard>
      <Typography variant="h5" fontWeight={700} textAlign="center" gutterBottom>
        Create Account
      </Typography>
      <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mb: 3 }}>
        Join Dev Knowledge Platform today
      </Typography>

      <Box component="form" onSubmit={handleSubmit}>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1}>
            <TextField
              fullWidth
              label="First Name"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              error={!!errors.firstName}
              helperText={errors.firstName}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <PersonIcon color="action" />
                  </InputAdornment>
                ),
              }}
            />
            <TextField
              fullWidth
              label="Last Name"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
            />
          </Stack>

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
            helperText={errors.password || 'At least 8 characters with uppercase, lowercase, and number'}
          />

          <PasswordField
            label="Confirm Password"
            value={confirmPassword}
            onChange={setConfirmPassword}
            error={!!errors.confirmPassword}
            helperText={errors.confirmPassword}
          />

          <SubmitButton type="submit" size="large" fullWidth saving={loading} label="Create Account" />
        </Stack>
      </Box>

      <Divider sx={{ my: 3 }}>
        <Typography variant="body2" color="text.secondary">
          or sign up with
        </Typography>
      </Divider>

      <SocialLoginButtons onSelect={loginWith} />

      <Box sx={{ mt: 3, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Already have an account?{' '}
          <Link component={RouterLink} to="/login" underline="hover" fontWeight="medium">
            Sign In
          </Link>
        </Typography>
      </Box>
    </AuthCard>
  );
}
