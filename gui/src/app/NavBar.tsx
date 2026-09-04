import { ReactNode } from 'react';
import { AppBar, Toolbar, Typography, Button, Box, Badge, IconButton, Tooltip, SxProps, Theme } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import ChecklistIcon from '@mui/icons-material/Checklist';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import StorefrontIcon from '@mui/icons-material/Storefront';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '@auth/services/authService';
import { useFriendRequestsCount } from '@friends/hooks/useFriendRequestsCount';
import { useCart } from '@ecommerce/context/CartContext';
import { ThemeMode } from './theme';

interface NavBarProps {
  mode: ThemeMode;
  onToggleMode: () => void;
}

interface NavButtonProps {
  active: boolean;
  startIcon: ReactNode;
  onClick: () => void;
  children: ReactNode;
  sx?: SxProps<Theme>;
}

/** The 7 nav-bar buttons (Shop + the 6 authenticated-only ones below) all share this exact shape —
 * `color="inherit" size="small"`, a `startIcon`, and an `action.selected` tint while the current
 * route matches. Extracted after that markup turned up duplicated 6× with, worse, two different
 * "is this active" checks (an exact-match `isActive` helper for Friends/Tasks/Cart, `location.
 * pathname.startsWith(...)` inlined for Account/Chat/Messages) — see gui/CLAUDE.md's style-audit
 * note. Login/Logout are deliberately NOT migrated onto this — neither has an "active route" concept
 * to highlight, so forcing them through a component built around that prop would be a poor fit. */
function NavButton({ active, startIcon, onClick, children, sx }: NavButtonProps): JSX.Element {
  return (
    <Button
      color="inherit"
      size="small"
      startIcon={startIcon}
      onClick={onClick}
      sx={{ backgroundColor: active ? 'action.selected' : 'transparent', ...sx }}
    >
      {children}
    </Button>
  );
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

  // startsWith, not an exact match — correctly keeps a nav item highlighted for any sub-route
  // (e.g. Account already relies on this for /account/profile, /account/addresses, etc.).
  const isActive = (path: string): boolean => location.pathname.startsWith(path);

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
        <NavButton
          active={isActive('/shop')}
          startIcon={<StorefrontIcon fontSize="small" />}
          onClick={() => navigate('/shop')}
          sx={{ mr: 0.5 }}
        >
          Shop
        </NavButton>

        {isAuthed && (
          <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
            {/* Leads into the Account shell (Profile + Addresses + Orders, AccountLayout's own
                sidebar) — one nav entry covers all three, rather than a separate top-level button
                per sub-page. Orders used to have its own dedicated button here before it moved
                into the Account shell (per request) — removed in that same change, same treatment
                Addresses itself already had. */}
            <NavButton
              active={isActive('/account')}
              startIcon={<AccountCircleIcon fontSize="small" />}
              onClick={() => navigate('/account/profile')}
            >
              Account
            </NavButton>

            <NavButton
              active={isActive('/chat')}
              startIcon={<SmartToyOutlinedIcon fontSize="small" />}
              onClick={() => navigate('/chat')}
            >
              Chat
            </NavButton>

            <NavButton
              active={isActive('/messages')}
              startIcon={<ChatBubbleOutlineIcon fontSize="small" />}
              onClick={() => navigate('/messages')}
            >
              Messages
            </NavButton>

            <NavButton
              active={isActive('/friends')}
              startIcon={
                <Badge badgeContent={friendRequestCount} color="error" max={9}>
                  <PeopleIcon fontSize="small" />
                </Badge>
              }
              onClick={() => navigate('/friends')}
            >
              Friends
            </NavButton>

            <NavButton
              active={isActive('/tasks')}
              startIcon={<ChecklistIcon fontSize="small" />}
              onClick={() => navigate('/tasks')}
            >
              Tasks
            </NavButton>

            <NavButton
              active={isActive('/cart')}
              startIcon={
                <Badge badgeContent={cart?.itemCount ?? 0} color="error" max={9}>
                  <ShoppingCartIcon fontSize="small" />
                </Badge>
              }
              onClick={() => navigate('/cart')}
            >
              Cart
            </NavButton>

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
