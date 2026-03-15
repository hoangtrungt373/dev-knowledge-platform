import { AppBar, Toolbar, Typography, Button, Box, Badge } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import DashboardIcon from '@mui/icons-material/Dashboard';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '../services';

export default function NavBar(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthed = authService.isAuthenticated();

  const handleLogout = (): void => {
    authService.logout();
    navigate('/login');
  };

  const isActive = (path: string): boolean => location.pathname === path;

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography 
          variant="h6" 
          sx={{ flexGrow: 1, cursor: 'pointer' }}
          onClick={() => navigate(isAuthed ? '/dashboard' : '/login')}
        >
          Duck Chat
        </Typography>
        
        {isAuthed && (
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              color="inherit"
              startIcon={<DashboardIcon />}
              onClick={() => navigate('/dashboard')}
              sx={{
                backgroundColor: isActive('/dashboard') ? '#ffffff1a' : 'transparent',
              }}
            >
              Dashboard
            </Button>
            
            <Button
              color="inherit"
              startIcon={
                <Badge color="error" variant="dot">
                  <PeopleIcon />
                </Badge>
              }
              onClick={() => navigate('/friends')}
              sx={{
                backgroundColor: isActive('/friends') ? '#ffffff1a' : 'transparent',
              }}
            >
              Friends
            </Button>
            
            <Button color="inherit" onClick={handleLogout}>
              Logout
            </Button>
          </Box>
        )}
        
        {!isAuthed && (
          <Button color="inherit" onClick={() => navigate('/login')}>
            Login
          </Button>
        )}
      </Toolbar>
    </AppBar>
  );
}
