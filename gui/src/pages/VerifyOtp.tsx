import React, { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Button,
  CircularProgress,
  Link,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import { authApi } from '../api';
import { authService } from '../services';
import { useNotification } from '../contexts/NotificationContext';
import { useSubmitGuard } from '../hooks/useSubmitGuard';

const OTP_LENGTH = 6;
const RESEND_COOLDOWN = 60;

export default function VerifyOtp(): JSX.Element {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { loading, guard } = useSubmitGuard();

  const email = searchParams.get('email') ?? '';

  const [digits, setDigits] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN);
  const [resending, setResending] = useState(false);
  const inputRefs = useRef<Array<HTMLInputElement | null>>(Array(OTP_LENGTH).fill(null));
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Redirect to signup if email param is missing
  useEffect(() => {
    if (!email) navigate('/signup', { replace: true });
  }, [email, navigate]);

  // Start resend cooldown on mount
  useEffect(() => {
    startCooldown();
    return () => stopCooldown();
  }, []);

  function startCooldown() {
    stopCooldown();
    setCooldown(RESEND_COOLDOWN);
    timerRef.current = setInterval(() => {
      setCooldown(prev => {
        if (prev <= 1) {
          stopCooldown();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }

  function stopCooldown() {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }

  const handleChange = (index: number, value: string) => {
    const digit = value.replace(/\D/g, '').slice(-1);
    const next = [...digits];
    next[index] = digit;
    setDigits(next);
    if (digit && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (digits[index]) {
        const next = [...digits];
        next[index] = '';
        setDigits(next);
      } else if (index > 0) {
        const next = [...digits];
        next[index - 1] = '';
        setDigits(next);
        inputRefs.current[index - 1]?.focus();
      }
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH);
    if (!pasted) return;
    const next = [...digits];
    pasted.split('').forEach((d, i) => { next[i] = d; });
    setDigits(next);
    const lastFilled = Math.min(pasted.length, OTP_LENGTH - 1);
    inputRefs.current[lastFilled]?.focus();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const otp = digits.join('');
    if (otp.length < OTP_LENGTH) return;
    guard(async () => {
      await authApi.verifyOtp(email, otp, showError);
      navigate('/dashboard', { replace: true });
    });
  };

  const handleResend = async () => {
    setResending(true);
    try {
      await authApi.resendOtp(email, showError);
      showSuccess('A new verification code has been sent to your email');
      setDigits(Array(OTP_LENGTH).fill(''));
      inputRefs.current[0]?.focus();
      startCooldown();
    } finally {
      setResending(false);
    }
  };

  const otp = digits.join('');
  const isComplete = otp.length === OTP_LENGTH;

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 420 }}>

        <Box display="flex" justifyContent="center" sx={{ mb: 2 }}>
          <MarkEmailReadIcon sx={{ fontSize: 48, color: 'primary.main' }} />
        </Box>

        <Typography variant="h5" fontWeight="bold" textAlign="center" gutterBottom>
          Check your email
        </Typography>
        <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mb: 1 }}>
          We sent a 6-digit code to
        </Typography>
        <Typography variant="body2" fontWeight="medium" textAlign="center" sx={{ mb: 3 }}>
          {email}
        </Typography>

        <Box component="form" onSubmit={handleSubmit}>
          <Stack direction="row" spacing={1} justifyContent="center" sx={{ mb: 3 }} onPaste={handlePaste}>
            {digits.map((digit, i) => (
              <TextField
                key={i}
                inputRef={el => { inputRefs.current[i] = el; }}
                value={digit}
                onChange={e => handleChange(i, e.target.value)}
                onKeyDown={e => handleKeyDown(i, e)}
                inputProps={{
                  maxLength: 1,
                  style: { textAlign: 'center', fontSize: '1.5rem', fontWeight: 600, padding: '12px 0' },
                }}
                sx={{ width: 52 }}
              />
            ))}
          </Stack>

          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            disabled={!isComplete || loading}
          >
            {loading ? <CircularProgress size={24} color="inherit" /> : 'Verify Email'}
          </Button>
        </Box>

        <Box sx={{ mt: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Didn't receive the code?{' '}
            {cooldown > 0 ? (
              <Typography component="span" variant="body2" color="text.disabled">
                Resend in {cooldown}s
              </Typography>
            ) : (
              <Link
                component="button"
                type="button"
                variant="body2"
                underline="hover"
                fontWeight="medium"
                onClick={handleResend}
                disabled={resending}
                sx={{ cursor: 'pointer' }}
              >
                {resending ? 'Sending…' : 'Resend code'}
              </Link>
            )}
          </Typography>
        </Box>

        <Box sx={{ mt: 2, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Wrong email?{' '}
            <Link
              component={RouterLink}
              to={authService.isAuthenticated() ? '/dashboard' : '/signup'}
              underline="hover"
              fontWeight="medium"
            >
              Go back
            </Link>
          </Typography>
        </Box>

      </Paper>
    </Box>
  );
}