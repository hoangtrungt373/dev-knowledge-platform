import { AppBar, Toolbar, Typography, Button, Box, Badge, IconButton, Tooltip } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import ChecklistIcon from '@mui/icons-material/Checklist';
import DashboardIcon from '@mui/icons-material/Dashboard';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '@auth/services/authService';
import { useFriendRequestsCount } from '@friends/hooks/useFriendRequestsCount';
import { ThemeMode } from './theme';

interface NavBarProps {
  mode: ThemeMode;
  onToggleMode: () => void;
}

export default function NavBar({ mode, onToggleMode }: NavBarProps): JSX.Element | null {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthed = authService.isAuthenticated();
  const hidden =
    location.pathname.startsWith('/admin') ||
    location.pathname.startsWith('/chat') ||
    location.pathname.startsWith('/messages');
  // Called unconditionally (Rules of Hooks) even on routes where NavBar renders null below —
  // `enabled={!hidden}` stops the poll itself on those routes rather than relying on
  // isAuthenticated() alone, which an admin session also satisfies.
  const { count: friendRequestCount } = useFriendRequestsCount(!hidden);

  if (hidden) return null;

  const handleLogout = (): void => {
    // authService.logout() already redirects to Keycloak's RP-initiated logout endpoint, which
    // itself redirects back to /login via post_logout_redirect_uri — a real full-page navigation.
    // A client-side navigate('/login') here would race that hard redirect (this app is still
    // mounted for a moment after window.location.href is set), causing /login to render twice:
    // once from this client-side route change, then again from the genuine post-logout page load.
    authService.logout();
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
              startIcon={<SmartToyOutlinedIcon fontSize="small" />}
              onClick={() => navigate('/chat')}
              sx={{
                backgroundColor: location.pathname.startsWith('/chat')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Chat
            </Button>

            <Button
              color="inherit"
              size="small"
              startIcon={<ChatBubbleOutlineIcon fontSize="small" />}
              onClick={() => navigate('/messages')}
              sx={{
                backgroundColor: location.pathname.startsWith('/messages')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Messages
            </Button>

            <Button
              color="inherit"
              size="small"
              startIcon={
                <Badge badgeContent={friendRequestCount} color="error" max={9}>
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

            <Button
              color="inherit"
              size="small"
              startIcon={<ChecklistIcon fontSize="small" />}
              onClick={() => navigate('/tasks')}
              sx={{
                backgroundColor: isActive('/tasks')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Tasks
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
