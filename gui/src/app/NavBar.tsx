import { AppBar, Toolbar, Typography, Button, Box, Badge, IconButton, Tooltip } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import ChecklistIcon from '@mui/icons-material/Checklist';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import StorefrontIcon from '@mui/icons-material/Storefront';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '@auth/services/authService';
import { useFriendRequestsCount } from '@friends/hooks/useFriendRequestsCount';
import { useCart } from '@ecommerce/context/CartContext';
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
  // Called unconditionally too (Rules of Hooks) — CartProvider always wraps NavBar, so this never
  // throws; the cart itself only has real data once the user is authenticated (see CartContext).
  const { cart, clear: clearCart } = useCart();

  if (hidden) return null;

  const handleLogout = (): void => {
    // Clears the badge immediately — authService.logout() redirects to Keycloak's RP-initiated
    // logout endpoint next, a real full-page navigation that would tear this state down anyway,
    // but this avoids a stale item count for the instant before that redirect lands.
    clearCart();
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

        {/* Shop is genuinely public (ecommerce-service's permitAll browse/search/detail) — the
            only nav entry visible regardless of auth state, unlike everything else below. */}
        <Button
          color="inherit"
          size="small"
          startIcon={<StorefrontIcon fontSize="small" />}
          onClick={() => navigate('/shop')}
          sx={{
            mr: 0.5,
            backgroundColor: location.pathname.startsWith('/shop') ? 'action.selected' : 'transparent',
          }}
        >
          Shop
        </Button>

        {isAuthed && (
          <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
            {/* Leads into the Account shell (Profile + Addresses, AccountLayout's own sidebar) —
                one nav entry covers both now, rather than a separate top-level button per
                sub-page. */}
            <Button
              color="inherit"
              size="small"
              startIcon={<AccountCircleIcon fontSize="small" />}
              onClick={() => navigate('/account/profile')}
              sx={{
                backgroundColor: location.pathname.startsWith('/account')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Account
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

            <Button
              color="inherit"
              size="small"
              startIcon={
                <Badge badgeContent={cart?.itemCount ?? 0} color="error" max={9}>
                  <ShoppingCartIcon fontSize="small" />
                </Badge>
              }
              onClick={() => navigate('/cart')}
              sx={{
                backgroundColor: isActive('/cart')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Cart
            </Button>

            <Button
              color="inherit"
              size="small"
              startIcon={<ReceiptLongIcon fontSize="small" />}
              onClick={() => navigate('/orders')}
              sx={{
                backgroundColor: location.pathname.startsWith('/orders')
                  ? 'action.selected'
                  : 'transparent',
              }}
            >
              Orders
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
