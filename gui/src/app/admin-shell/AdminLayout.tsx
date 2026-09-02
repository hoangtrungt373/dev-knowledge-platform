import { useEffect, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Collapse,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
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
import TuneIcon from '@mui/icons-material/Tune';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import DiscountIcon from '@mui/icons-material/Discount';
import ArticleIcon from '@mui/icons-material/Article';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import StorefrontIcon from '@mui/icons-material/Storefront';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
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

interface NavLeaf {
  label: string;
  icon: JSX.Element;
  path: string;
}

interface NavGroup {
  label: string;
  icon: JSX.Element;
  children: NavLeaf[];
}

type NavEntry = NavLeaf | NavGroup;

function isGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry;
}

/** Grouped by which backend module each section fronts — mirrors this reactor's own
 * one-module-per-vertical-slice structure (see root CLAUDE.md), so the sidebar's own grouping
 * needs no separate design decision of its own. `Overview` stays a standalone leaf (the landing
 * page, not really "owned" by any one module). */
const NAV_STRUCTURE: NavEntry[] = [
  { label: 'Overview', icon: <DashboardIcon fontSize="small" />, path: '/admin/dashboard' },
  {
    label: 'Content',
    icon: <ArticleIcon fontSize="small" />,
    children: [
      { label: 'Tags', icon: <LabelIcon fontSize="small" />, path: '/admin/tags' },
      { label: 'Categories', icon: <FolderIcon fontSize="small" />, path: '/admin/categories' },
      { label: 'Questions & Answers', icon: <QuizIcon fontSize="small" />, path: '/admin/question-answers' },
    ],
  },
  {
    label: 'AI',
    icon: <SmartToyIcon fontSize="small" />,
    children: [
      { label: 'Pipeline Metrics', icon: <QueryStatsIcon fontSize="small" />, path: '/admin/pipeline-metrics' },
      { label: 'Embeddings', icon: <DataArrayIcon fontSize="small" />, path: '/admin/embeddings' },
    ],
  },
  {
    label: 'Ecommerce',
    icon: <StorefrontIcon fontSize="small" />,
    children: [
      { label: 'Product Categories', icon: <CategoryIcon fontSize="small" />, path: '/admin/product-categories' },
      { label: 'Products', icon: <Inventory2Icon fontSize="small" />, path: '/admin/products' },
      { label: 'Product Tags', icon: <SellIcon fontSize="small" />, path: '/admin/product-tags' },
      { label: 'Product Attributes', icon: <TuneIcon fontSize="small" />, path: '/admin/product-attributes' },
      { label: 'Order Fulfillment', icon: <LocalShippingIcon fontSize="small" />, path: '/admin/orders' },
      { label: 'Coupons', icon: <DiscountIcon fontSize="small" />, path: '/admin/coupons' },
    ],
  },
];

export default function AdminLayout(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const adminUser = adminAuthService.getAdminUser();

  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true',
  );
  const sidebarWidth = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;

  const isActive = (path: string) => location.pathname.startsWith(path);

  // Which groups are currently expanded (accordion-style, but independent — more than one can be
  // open at once). Seeded with whichever group contains the current route, so a direct URL nav
  // (not just clicking through the sidebar itself) lands with the right section already open.
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(() => {
    const active = NAV_STRUCTURE.find(entry => isGroup(entry) && entry.children.some(c => isActive(c.path)));
    return active ? new Set([active.label]) : new Set();
  });

  // Only ever adds, never auto-collapses a group the caller (or an earlier navigation) already
  // opened — re-expands the active section on every route change without fighting a group the
  // admin deliberately left open for an unrelated section.
  useEffect(() => {
    const active = NAV_STRUCTURE.find(entry => isGroup(entry) && entry.children.some(c => isActive(c.path)));
    if (active) {
      setExpandedGroups(prev => (prev.has(active.label) ? prev : new Set(prev).add(active.label)));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  const toggleGroup = (label: string): void => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(label)) next.delete(label); else next.add(label);
      return next;
    });
  };

  // Collapsed sidebar has no room for an inline-expanding group — a parent icon click opens this
  // flyout Menu instead, listing that group's children (same idea as a collapsed OS dock's
  // submenu). Unrelated to the expanded-sidebar accordion state above, which stays untouched
  // while collapsed.
  const [flyout, setFlyout] = useState<{ anchorEl: HTMLElement; group: NavGroup } | null>(null);

  const toggleCollapsed = (): void => {
    setCollapsed(prev => {
      const next = !prev;
      localStorage.setItem(COLLAPSE_STORAGE_KEY, String(next));
      return next;
    });
    setFlyout(null);
  };

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
        <List dense disablePadding sx={{ pt: 0.5, flex: 1, overflowY: 'auto' }}>
          {NAV_STRUCTURE.map(entry => {
            if (!isGroup(entry)) {
              return (
                <Tooltip key={entry.path} title={collapsed ? entry.label : ''} placement="right">
                  <ListItemButton
                    selected={isActive(entry.path)}
                    onClick={() => navigate(entry.path)}
                    sx={{ borderRadius: 1, mx: 0.5, mb: 0.25, justifyContent: collapsed ? 'center' : 'flex-start' }}
                  >
                    <ListItemIcon sx={{ minWidth: collapsed ? 0 : 30, justifyContent: 'center' }}>
                      {entry.icon}
                    </ListItemIcon>
                    {!collapsed && (
                      <ListItemText primary={entry.label} primaryTypographyProps={{ variant: 'body2' }} />
                    )}
                  </ListItemButton>
                </Tooltip>
              );
            }

            const groupActive = entry.children.some(c => isActive(c.path));
            const expanded = expandedGroups.has(entry.label);

            if (collapsed) {
              return (
                <Tooltip key={entry.label} title={entry.label} placement="right">
                  <ListItemButton
                    selected={groupActive}
                    onClick={e => setFlyout({ anchorEl: e.currentTarget, group: entry })}
                    sx={{ borderRadius: 1, mx: 0.5, mb: 0.25, justifyContent: 'center' }}
                  >
                    <ListItemIcon sx={{ minWidth: 0, justifyContent: 'center' }}>{entry.icon}</ListItemIcon>
                  </ListItemButton>
                </Tooltip>
              );
            }

            return (
              <Box key={entry.label}>
                <ListItemButton
                  selected={groupActive}
                  onClick={() => toggleGroup(entry.label)}
                  sx={{ borderRadius: 1, mx: 0.5, mb: 0.25 }}
                >
                  <ListItemIcon sx={{ minWidth: 30 }}>{entry.icon}</ListItemIcon>
                  <ListItemText primary={entry.label} primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }} />
                  {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                </ListItemButton>
                <Collapse in={expanded} timeout="auto" unmountOnExit>
                  <List dense disablePadding>
                    {entry.children.map(child => (
                      <ListItemButton
                        key={child.path}
                        selected={isActive(child.path)}
                        onClick={() => navigate(child.path)}
                        sx={{ borderRadius: 1, mx: 0.5, mb: 0.25, pl: 4 }}
                      >
                        <ListItemIcon sx={{ minWidth: 26 }}>{child.icon}</ListItemIcon>
                        <ListItemText primary={child.label} primaryTypographyProps={{ variant: 'body2' }} />
                      </ListItemButton>
                    ))}
                  </List>
                </Collapse>
              </Box>
            );
          })}
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

      {/* Collapsed-sidebar flyout submenu for a group — see the `collapsed` branch above */}
      <Menu
        anchorEl={flyout?.anchorEl ?? null}
        open={!!flyout}
        onClose={() => setFlyout(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        {flyout?.group.children.map(child => (
          <MenuItem
            key={child.path}
            selected={isActive(child.path)}
            onClick={() => { navigate(child.path); setFlyout(null); }}
          >
            <ListItemIcon sx={{ minWidth: 30 }}>{child.icon}</ListItemIcon>
            <ListItemText primary={child.label} primaryTypographyProps={{ variant: 'body2' }} />
          </MenuItem>
        ))}
      </Menu>

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
