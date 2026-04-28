import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import { authService } from '../services';
import { authApi } from '../api';
import { useNotification } from '../contexts/NotificationContext';

/**
 * AuthCallback Component - Option 3: State Parameter Approach
 * 
 * Flow:
 * 1. Backend redirects: /auth/callback?state=uuid-token
 * 2. Extract state token from URL
 * 3. Call API to exchange state token for JWT tokens
 * 4. Store tokens in localStorage
 * 5. Navigate to dashboard
 */
export default function AuthCallback(): JSX.Element | null {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { showError, showSuccess } = useNotification();

  useEffect(() => {
    const exchangeStateToken = async () => {
      try {
        // Step 1: Extract state token from URL
        // URL: http://localhost:3000/auth/callback?state=550e8400-e29b-41d4-a716-446655440000
        const stateToken = searchParams.get('state');
        
        if (!stateToken) {
          const errorMsg = 'Missing state token';
          setError(errorMsg);
          setLoading(false);
          showError(errorMsg);
          navigate('/login?error=missing_state', { replace: true });
          return;
        }
        
        console.log('State token received:', stateToken);
        
        // Step 2: Exchange state token for actual JWT tokens
        // Api.exchangeStateToken automatically handles errors and shows notifications
        const data = await authApi.exchangeStateToken(stateToken, showError);
        
        // Step 3: Store tokens in localStorage
        authService.storeTokens({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
          userId: data.userId,
          username: data.username,
          email: data.email,
        });
        
        console.log('Tokens stored in localStorage');
        
        // Step 4: Show success and navigate to dashboard
        showSuccess('Login successful!');
        navigate('/dashboard', { replace: true });
        
      } catch (err: any) {
        console.error('Token exchange failed:', err);
        const errorMsg = err.message || 'Failed to exchange token';
        setError(errorMsg);
        setLoading(false);
        
        // Error notification already shown by Api.exchangeStateToken
        // Redirect to login after a delay
        setTimeout(() => {
          navigate('/login?error=token_exchange_failed', { replace: true });
        }, 2000);
      }
    };
    
    // Execute token exchange
    exchangeStateToken();
  }, [navigate, searchParams, showError, showSuccess]);

  // Show loading state
  if (loading && !error) {
    return (
      <Box 
        display="flex" 
        flexDirection="column" 
        alignItems="center" 
        justifyContent="center" 
        minHeight="60vh"
      >
        <CircularProgress size={60} />
        <Typography variant="body1" sx={{ mt: 3 }}>
          Exchanging authentication token...
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Please wait while we complete your login
        </Typography>
      </Box>
    );
  }
  
  // Show error state
  if (error) {
    return (
      <Box 
        display="flex" 
        flexDirection="column" 
        alignItems="center" 
        justifyContent="center" 
        minHeight="60vh"
        sx={{ px: 2 }}
      >
        <Alert severity="error" sx={{ mb: 2, maxWidth: 500 }}>
          <Typography variant="h6">Authentication Failed</Typography>
          <Typography variant="body2">{error}</Typography>
        </Alert>
        <Typography variant="body2" color="text.secondary">
          Redirecting to login page...
        </Typography>
      </Box>
    );
  }
  
  return null;
}

