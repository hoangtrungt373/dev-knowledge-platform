import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  Divider,
  Chip,
} from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import LogoutIcon from '@mui/icons-material/Logout';
import { adminAuthService } from '../services';

export default function AdminDashboard(): JSX.Element {
  const navigate = useNavigate();

  if (!adminAuthService.isAuthenticated()) {
    navigate('/admin/login', { replace: true });
  }

  const adminUser = adminAuthService.getAdminUser();

  const handleLogout = (): void => {
    adminAuthService.logout();
  };

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 600 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <AdminPanelSettingsIcon sx={{ fontSize: 40, color: 'primary.main' }} />
          <Box>
            <Typography variant="h5" fontWeight="bold">
              Admin Dashboard
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Welcome to the admin panel
            </Typography>
          </Box>
        </Stack>

        <Divider sx={{ my: 3 }} />

        {adminUser && (
          <Stack spacing={1} sx={{ mb: 3 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary" sx={{ width: 80 }}>
                Username
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {adminUser.username}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary" sx={{ width: 80 }}>
                Email
              </Typography>
              <Typography variant="body1">{adminUser.email}</Typography>
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary" sx={{ width: 80 }}>
                Role
              </Typography>
              <Chip label="ADMIN" color="primary" size="small" />
            </Stack>
          </Stack>
        )}

        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
          This is a placeholder admin dashboard. Future features will include user management,
          system statistics, and configuration settings.
        </Typography>

        <Stack direction="row" spacing={2}>
          <Button
            variant="contained"
            color="error"
            startIcon={<LogoutIcon />}
            onClick={handleLogout}
          >
            Logout
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}