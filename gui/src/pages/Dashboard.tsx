import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Paper, Typography, Avatar, Stack, Divider, Button, CircularProgress, Box } from '@mui/material';
import { userApi } from '../api';
import { authService } from '../services';
import { User } from '../types';
import { useNotification } from '../contexts/NotificationContext';

export default function Dashboard(): JSX.Element | null {
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const { showError } = useNotification();

  useEffect(() => {
    if (!authService.isAuthenticated()) {
      navigate('/login', { replace: true });
      return;
    }

    (async () => {
      try {
        setLoading(true);
        // Api.getCurrentUser automatically shows error notification if it fails
        const me = await userApi.getCurrentUser(showError);
        setUser(me);
      } catch (error) {
        console.error('Failed to load user:', error);
        // Error notification already shown by Api.getCurrentUser
        // Optionally redirect to login if unauthorized
        if ((error as any)?.status === 401) {
          authService.logout();
        }
      } finally {
        setLoading(false);
      }
    })();
  }, [navigate, showError]);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  const displayName = user?.firstName && user?.lastName
    ? `${user.firstName} ${user.lastName}`
    : user?.username;

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 600 }}>
        <Typography variant="h5" fontWeight="bold" gutterBottom>Welcome</Typography>
        {user ? (
          <Stack direction="row" spacing={2} alignItems="center">
            <Avatar src={user.profilePicture} alt={displayName} />
            <Box sx={{ textAlign: 'left' }}>
              <Typography variant="subtitle1">{displayName}</Typography>
              <Typography variant="body2" color="text.secondary">{user.email}</Typography>
            </Box>
          </Stack>
        ) : (
          <Typography>No user data available</Typography>
        )}
        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2}>
          <Button variant="contained" onClick={() => authService.logout()}>Logout</Button>
        </Stack>
      </Paper>
    </Box>
  );
}
