import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Box, Divider, Drawer, List, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';

const SIDEBAR_WIDTH = 220;

const NAV_ITEMS = [
  { label: 'Profile', icon: <PersonOutlineIcon fontSize="small" />, path: '/account/profile' },
  { label: 'Addresses', icon: <LocationOnOutlinedIcon fontSize="small" />, path: '/account/addresses' },
];

/**
 * Shell for the shopper's own account area — `Profile` (identity-service's own user profile,
 * `@auth/pages/ProfilePage.tsx`) and `Addresses` (ecommerce-service's AddressBook,
 * `@ecommerce/pages/AddressBookPage.tsx`). Lives directly under `app/`, not inside either feature
 * — same reasoning `admin-shell/` is neutral rather than owned by `@content`/`@ecommerce`: this
 * shell's two destinations span two unrelated features, so it can't fairly belong to either one.
 *
 * <p>Deliberately simpler than `AdminLayout`'s own sidebar — no collapse toggle/`localStorage`
 * persistence, since there are only two destinations today; that complexity earned its keep on
 * `AdminLayout` with 9+ nav items, not here. Revisit if this grows a third or fourth section
 * (Security, Notifications, Payment Methods, …) and the sidebar starts feeling cramped.
 *
 * <p>Unlike `AdminLayout`, the top `NavBar` stays visible above this shell — the account area is
 * still very much "part of the site" (every other non-admin, non-full-screen page keeps `NavBar`
 * too), not a separate back-office experience with its own replacement chrome, so this shell has
 * no `height: '100vh'`/`overflow: hidden` of its own and no "Back to Site"/"Logout" nav of its
 * own either — `NavBar`'s existing Logout button already covers that.
 */
export default function AccountLayout(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const isActive = (path: string): boolean => location.pathname.startsWith(path);

  return (
    <Box sx={{ display: 'flex' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: SIDEBAR_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: SIDEBAR_WIDTH,
            boxSizing: 'border-box',
            borderRight: '1px solid',
            borderColor: 'divider',
          },
        }}
      >
        <Box sx={{ px: 2, py: 1.5 }}>
          <Typography variant="subtitle2" fontWeight={700} color="primary">
            My Account
          </Typography>
        </Box>
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
      </Drawer>

      <Box component="main" sx={{ flex: 1, minWidth: 0 }}>
        <Outlet />
      </Box>
    </Box>
  );
}
