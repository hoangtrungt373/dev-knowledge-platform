import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
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
import HomeIcon from '@mui/icons-material/Home';
import LogoutIcon from '@mui/icons-material/Logout';
import { adminAuthService } from '../../services';

const SIDEBAR_WIDTH = 220;

const NAV_ITEMS = [
  { label: 'Overview',              icon: <DashboardIcon fontSize="small" />,  path: '/admin/dashboard' },
  { label: 'Tags',                  icon: <LabelIcon fontSize="small" />,      path: '/admin/tags' },
  { label: 'Categories',            icon: <FolderIcon fontSize="small" />,     path: '/admin/categories' },
  { label: 'Questions & Answers',   icon: <QuizIcon fontSize="small" />,       path: '/admin/question-answers' },
  { label: 'Pipeline Metrics',      icon: <QueryStatsIcon fontSize="small" />, path: '/admin/pipeline-metrics' },
  { label: 'Embeddings',            icon: <DataArrayIcon fontSize="small" />,  path: '/admin/embeddings' },
];

export default function AdminLayout(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const adminUser = adminAuthService.getAdminUser();

  const isActive = (path: string) => location.pathname.startsWith(path);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>

      {/* ── Sidebar ── */}
      <Drawer
        variant="permanent"
        sx={{
          width: SIDEBAR_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: SIDEBAR_WIDTH,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            borderRight: '1px solid',
            borderColor: 'divider',
          },
        }}
      >
        {/* Brand */}
        <Box sx={{ px: 2, py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Typography
            variant="subtitle2"
            fontWeight={700}
            color="primary"
            sx={{ cursor: 'pointer' }}
            onClick={() => navigate('/admin/dashboard')}
          >
            DKP Admin
          </Typography>
        </Box>

        {/* Nav items */}
        <List dense disablePadding sx={{ pt: 0.5, flex: 1 }}>
          {NAV_ITEMS.map(item => (
            <ListItemButton
              key={item.path}
              selected={isActive(item.path)}
              onClick={() => navigate(item.path)}
              sx={{ borderRadius: 1, mx: 0.5, mb: 0.25 }}
            >
              <ListItemIcon sx={{ minWidth: 30 }}>{item.icon}</ListItemIcon>
              <ListItemText
                primary={item.label}
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItemButton>
          ))}
        </List>

        {/* Bottom actions */}
        <Divider />
        <List dense disablePadding sx={{ py: 0.5 }}>
          <ListItemButton
            onClick={() => navigate('/dashboard')}
            sx={{ borderRadius: 1, mx: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 30 }}>
              <HomeIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText
              primary="Back to Site"
              primaryTypographyProps={{ variant: 'body2' }}
            />
          </ListItemButton>

          <ListItemButton
            onClick={() => adminAuthService.logout()}
            sx={{ borderRadius: 1, mx: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 30 }}>
              <LogoutIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText
              primary="Logout"
              primaryTypographyProps={{ variant: 'body2' }}
            />
          </ListItemButton>
        </List>

        {/* User info */}
        {adminUser && (
          <Box
            sx={{
              px: 1.5,
              py: 1,
              borderTop: '1px solid',
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
          >
            <Avatar sx={{ width: 26, height: 26, fontSize: '0.7rem', bgcolor: 'primary.main' }}>
              {adminUser.username[0].toUpperCase()}
            </Avatar>
            <Box sx={{ overflow: 'hidden', minWidth: 0 }}>
              <Typography variant="caption" display="block" noWrap fontWeight={600}>
                {adminUser.username}
              </Typography>
              <Typography variant="caption" display="block" noWrap color="text.secondary">
                {adminUser.email}
              </Typography>
            </Box>
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
