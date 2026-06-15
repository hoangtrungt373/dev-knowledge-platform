import { AppBar, Toolbar, Typography, Button, Box, Badge, IconButton, Tooltip } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import DashboardIcon from '@mui/icons-material/Dashboard';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '../services';
import { ThemeMode } from '../theme';

interface NavBarProps {
  mode: ThemeMode;
  onToggleMode: () => void;
}

export default function NavBar({ mode, onToggleMode }: NavBarProps): JSX.Element | null {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthed = authService.isAuthenticated();

  if (location.pathname.startsWith('/admin')) return null;

  const handleLogout = (): void => {
    authService.logout();
    navigate('/login');
  };

  const isActive = (path: string): boolean => location.pathname === path;

  return (
    <AppBar position="static">
      <Toolbar variant="dense">
        <Typography
          variant="h6"
          sx={{ flexGrow: 1, cursor: 'pointer', fontWeight: 700, fontSize: '1rem' }}
          onClick={() => navigate(isAuthed ? '/dashboard' : '/login')}
        >
          Dev Knowledge Platform
        </Typography>

        {isAuthed && (
          <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
            <Button
              color="inherit"
              size="small"
              startIcon={<DashboardIcon fontSize="small" />}
              onClick={() => navigate('/dashboard')}
              sx={{
                backgroundColor: isActive('/dashboard')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Dashboard
            </Button>

            <Button
              color="inherit"
              size="small"
              startIcon={
                <Badge color="error" variant="dot">
                  <PeopleIcon fontSize="small" />
                </Badge>
              }
              onClick={() => navigate('/friends')}
              sx={{
                backgroundColor: isActive('/friends')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Friends
            </Button>

            <Button color="inherit" size="small" onClick={handleLogout}>
              Logout
            </Button>
          </Box>
        )}

        {!isAuthed && (
          <Button color="inherit" size="small" onClick={() => navigate('/login')}>
            Login
          </Button>
        )}

        <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
          <IconButton color="inherit" onClick={onToggleMode} sx={{ ml: 1 }}>
            {mode === 'dark' ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
          </IconButton>
        </Tooltip>
      </Toolbar>
    </AppBar>
  );
}
