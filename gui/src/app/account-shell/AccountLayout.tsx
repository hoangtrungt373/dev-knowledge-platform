import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Box, Divider, List, ListItemButton, ListItemIcon, ListItemText, Paper, Typography } from '@mui/material';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { profileApi } from '@auth/api/profileApi';
import { User } from '@auth/types';

const NAV_ITEMS = [
  { label: 'Profile', icon: <PersonOutlineIcon fontSize="small" />, path: '/account/profile' },
  { label: 'Addresses', icon: <LocationOnOutlinedIcon fontSize="small" />, path: '/account/addresses' },
  { label: 'Orders', icon: <ReceiptLongOutlinedIcon fontSize="small" />, path: '/account/orders' },
];

/**
 * Shell for the shopper's own account area — `Profile` (identity-service's own user profile,
 * `@auth/pages/ProfilePage.tsx`), `Addresses` (ecommerce-service's AddressBook,
 * `@ecommerce/pages/AddressBookPage.tsx`), and `Orders` (ecommerce-service's own order history/
 * detail, `@ecommerce/pages/orders/{OrderHistoryPage,OrderDetailPage}.tsx` — moved here from their
 * own top-level `/orders`/`/orders/:id` routes per request; `NavBar.tsx`'s own dedicated "Orders"
 * button was removed in the same change, since its "Account" button already covers everything
 * under `/account/**`, same treatment `Addresses` itself already had). Lives directly under
 * `app/`, not inside any one feature — same reasoning `admin-shell/` is neutral rather than owned
 * by `@content`/`@ecommerce`: this shell's destinations span multiple unrelated features, so it
 * can't fairly belong to any one of them.
 *
 * <p>Deliberately simpler than `AdminLayout`'s own sidebar — no collapse toggle/`localStorage`
 * persistence, since there are only three destinations today; that complexity earned its keep on
 * `AdminLayout` with 9+ nav items, not here. Revisit if this grows further
 * (Security, Notifications, Payment Methods, …) and the sidebar starts feeling cramped.
 *
 * <p>Unlike `AdminLayout`, the top `NavBar` stays visible above this shell — the account area is
 * still very much "part of the site," not a separate back-office experience with its own
 * replacement chrome.
 *
 * <p><strong>Sidebar is a plain `Paper`, not an MUI `Drawer`</strong> — deliberately, after a real
 * `Drawer` attempt caused two separate, related bugs. `variant="permanent"` still renders its
 * `Paper` slot `position: fixed` by default in MUI's own baked-in `styled()` definition, and
 * overriding that via a nested `sx` selector (`'& .MuiDrawer-paper': { position: 'static' }`) is
 * unreliable — it can lose to MUI's own higher-specificity internal styles depending on emotion's
 * style-injection order, which is exactly what caused both reported symptoms at once: the sidebar
 * sitting on top of (or too close to) `NavBar` (evidence the `fixed` override wasn't reliably
 * winning), and the sidebar's own rendered width changing between `/account/profile` and
 * `/account/addresses` (a `fixed`-position element's own `%` width resolves against the *viewport*,
 * not this flex row, so any inconsistency in whether the override applied would show up as exactly
 * this kind of width flicker). None of `Drawer`'s actual features — overlay/backdrop, swipe,
 * elevation transitions — are used here anyway; a plain `Paper` sidesteps the whole class of bug
 * by construction, since it never had baked-in fixed positioning to begin with.
 */
export default function AccountLayout(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const isActive = (path: string): boolean => location.pathname.startsWith(path);

  // Lightweight — just avatar + display name for the sidebar header. A failure here is silent:
  // this is a cosmetic nicety, and ProfilePage.tsx's own fetch already handles a real failure
  // (e.g. a 401) as its actual concern.
  const [user, setUser] = useState<User | null>(null);
  useEffect(() => {
    profileApi.getCurrentUser().then(setUser).catch(() => {});
  }, []);

  const displayName = user
    ? (user.firstName ? `${user.firstName}${user.lastName ? ` ${user.lastName}` : ''}` : user.username)
    : '';
  const initials = user
    ? (user.firstName ? `${user.firstName[0]}${user.lastName ? user.lastName[0] : ''}`.toUpperCase() : user.username[0].toUpperCase())
    : '';

  return (
    // alignItems: 'flex-start', not the flex default of 'stretch' — without it, the sidebar
    // Paper stretches to match the content column's height (often much taller than the sidebar's
    // own compact avatar+2-nav-items content), leaving a large empty area inside its own border
    // and making the actual nav content look disconnected from the box drawn around it. flex-start
    // sizes the sidebar to its own content and pins both columns to the top of the row instead.
    <Box sx={{ display: 'flex', alignItems: 'flex-start', width: '80%', mx: 'auto', mt: 3, mb: 3 }}>
      <Paper
        variant="outlined"
        sx={{ width: '20%', flexShrink: 0, bgcolor: 'background.paper', overflow: 'hidden' }}
      >
        {user && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 2, minWidth: 0 }}>
            <Avatar src={user.profilePicture} sx={{ width: 40, height: 40, flexShrink: 0 }}>
              {initials}
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" fontWeight={600} noWrap>{displayName}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>@{user.username}</Typography>
            </Box>
          </Box>
        )}
        <Divider />
        <List dense disablePadding sx={{ pt: 0.5 }}>
          {NAV_ITEMS.map(item => (
            <ListItemButton
              key={item.path}
              selected={isActive(item.path)}
              onClick={() => navigate(item.path)}
              sx={{ borderRadius: 1, mx: 0.5, mb: 0.25 }}
            >
              <ListItemIcon sx={{ minWidth: 32 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} primaryTypographyProps={{ variant: 'body2' }} />
            </ListItemButton>
          ))}
        </List>
      </Paper>

      <Box component="main" sx={{ flex: 1, minWidth: 0 }}>
        <Outlet />
      </Box>
    </Box>
  );
}
