import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Typography,
  Divider,
  Avatar,
} from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import LabelIcon from '@mui/icons-material/Label';
import FolderIcon from '@mui/icons-material/Folder';
import QuizIcon from '@mui/icons-material/Quiz';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import DataArrayIcon from '@mui/icons-material/DataArray';
import CategoryIcon from '@mui/icons-material/Category';
import Inventory2Icon from '@mui/icons-material/Inventory2';
import SellIcon from '@mui/icons-material/Sell';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import HomeIcon from '@mui/icons-material/Home';
import LogoutIcon from '@mui/icons-material/Logout';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { adminAuthService } from '@auth/services/adminAuthService';

const EXPANDED_WIDTH = 220;
const COLLAPSED_WIDTH = 64;
// Persisted so the choice survives a reload — this is a standing preference (like a theme
// choice), not per-session UI state, so localStorage is the right tool per this app's own
// convention (see the artifact/browser-storage guidance this reactor otherwise follows).
const COLLAPSE_STORAGE_KEY = 'adminSidebarCollapsed';

const NAV_ITEMS = [
  { label: 'Overview',              icon: <DashboardIcon fontSize="small" />,  path: '/admin/dashboard' },
  { label: 'Tags',                  icon: <LabelIcon fontSize="small" />,      path: '/admin/tags' },
  { label: 'Categories',            icon: <FolderIcon fontSize="small" />,     path: '/admin/categories' },
  { label: 'Questions & Answers',   icon: <QuizIcon fontSize="small" />,       path: '/admin/question-answers' },
  { label: 'Pipeline Metrics',      icon: <QueryStatsIcon fontSize="small" />, path: '/admin/pipeline-metrics' },
  { label: 'Embeddings',            icon: <DataArrayIcon fontSize="small" />,  path: '/admin/embeddings' },
  { label: 'Product Categories',    icon: <CategoryIcon fontSize="small" />,   path: '/admin/product-categories' },
  { label: 'Products',              icon: <Inventory2Icon fontSize="small" />, path: '/admin/products' },
  { label: 'Product Tags',          icon: <SellIcon fontSize="small" />,       path: '/admin/product-tags' },
  { label: 'Order Fulfillment',     icon: <LocalShippingIcon fontSize="small" />, path: '/admin/orders' },
];

export default function AdminLayout(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const adminUser = adminAuthService.getAdminUser();

  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true',
  );
  const sidebarWidth = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;

  const toggleCollapsed = (): void => {
    setCollapsed(prev => {
      const next = !prev;
      localStorage.setItem(COLLAPSE_STORAGE_KEY, String(next));
      return next;
    });
  };

  const isActive = (path: string) => location.pathname.startsWith(path);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>

      {/* ── Sidebar ── */}
      <Drawer
        variant="permanent"
        sx={{
          width: sidebarWidth,
          flexShrink: 0,
          // Animates the width change instead of snapping — this Drawer renders in normal
          // document flow (variant="permanent" is not position: fixed), so the main content's
          // own flex: 1 reflows to fill the freed space automatically; no matching margin/
          // transition needed over there.
          transition: theme => theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
          '& .MuiDrawer-paper': {
            width: sidebarWidth,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            borderRight: '1px solid',
            borderColor: 'divider',
            overflowX: 'hidden',
            transition: theme => theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
          },
        }}
      >
        {/* Brand + collapse toggle */}
        <Box
          sx={{
            px: collapsed ? 0 : 2,
            py: 1.5,
            borderBottom: '1px solid',
            borderColor: 'divider',
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'space-between',
          }}
        >
          {!collapsed && (
            <Typography
              variant="subtitle2"
              fontWeight={700}
              color="primary"
              noWrap
              sx={{ cursor: 'pointer' }}
              onClick={() => navigate('/admin/dashboard')}
            >
              DKP Admin
            </Typography>
          )}
          <Tooltip title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} placement="right">
            <IconButton size="small" onClick={toggleCollapsed}>
              {collapsed ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Box>

        {/* Nav items */}
        <List dense disablePadding sx={{ pt: 0.5, flex: 1 }}>
          {NAV_ITEMS.map(item => (
            <Tooltip key={item.path} title={collapsed ? item.label : ''} placement="right">
              <ListItemButton
                selected={isActive(item.path)}
                onClick={() => navigate(item.path)}
                sx={{ borderRadius: 1, mx: 0.5, mb: 0.25, justifyContent: collapsed ? 'center' : 'flex-start' }}
              >
                <ListItemIcon sx={{ minWidth: collapsed ? 0 : 30, justifyContent: 'center' }}>
                  {item.icon}
                </ListItemIcon>
                {!collapsed && (
                  <ListItemText primary={item.label} primaryTypographyProps={{ variant: 'body2' }} />
                )}
              </ListItemButton>
            </Tooltip>
          ))}
        </List>

        {/* Bottom actions */}
        <Divider />
        <List dense disablePadding sx={{ py: 0.5 }}>
          <Tooltip title={collapsed ? 'Back to Site' : ''} placement="right">
            <ListItemButton
              onClick={() => navigate('/dashboard')}
              sx={{ borderRadius: 1, mx: 0.5, justifyContent: collapsed ? 'center' : 'flex-start' }}
            >
              <ListItemIcon sx={{ minWidth: collapsed ? 0 : 30, justifyContent: 'center' }}>
                <HomeIcon fontSize="small" />
              </ListItemIcon>
              {!collapsed && (
                <ListItemText primary="Back to Site" primaryTypographyProps={{ variant: 'body2' }} />
              )}
            </ListItemButton>
          </Tooltip>

          <Tooltip title={collapsed ? 'Logout' : ''} placement="right">
            <ListItemButton
              onClick={() => adminAuthService.logout()}
              sx={{ borderRadius: 1, mx: 0.5, justifyContent: collapsed ? 'center' : 'flex-start' }}
            >
              <ListItemIcon sx={{ minWidth: collapsed ? 0 : 30, justifyContent: 'center' }}>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              {!collapsed && (
                <ListItemText primary="Logout" primaryTypographyProps={{ variant: 'body2' }} />
              )}
            </ListItemButton>
          </Tooltip>
        </List>

        {/* User info */}
        {adminUser && (
          <Box
            sx={{
              px: collapsed ? 0 : 1.5,
              py: 1,
              borderTop: '1px solid',
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'center',
              justifyContent: collapsed ? 'center' : 'flex-start',
              gap: 1,
            }}
          >
            <Tooltip title={collapsed ? `${adminUser.username} (${adminUser.email})` : ''} placement="right">
              <Avatar sx={{ width: 26, height: 26, fontSize: '0.7rem', bgcolor: 'primary.main' }}>
                {adminUser.username[0].toUpperCase()}
              </Avatar>
            </Tooltip>
            {!collapsed && (
              <Box sx={{ overflow: 'hidden', minWidth: 0 }}>
                <Typography variant="caption" display="block" noWrap fontWeight={600}>
                  {adminUser.username}
                </Typography>
                <Typography variant="caption" display="block" noWrap color="text.secondary">
                  {adminUser.email}
                </Typography>
              </Box>
            )}
          </Box>
        )}
      </Drawer>

      {/* ── Main content ── */}
      <Box
        component="main"
        sx={{
          flex: 1,
          overflow: 'auto',
          bgcolor: 'background.default',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}
