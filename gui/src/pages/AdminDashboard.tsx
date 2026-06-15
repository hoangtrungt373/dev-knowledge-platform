import { Box, Typography, Paper, Stack, Divider, Chip } from '@mui/material';
import LabelIcon from '@mui/icons-material/Label';
import FolderIcon from '@mui/icons-material/Folder';
import ArticleIcon from '@mui/icons-material/Article';
import QuizIcon from '@mui/icons-material/Quiz';
import { adminAuthService } from '../services';

const FEATURE_CARDS = [
  {
    label: 'Tags',
    icon: <LabelIcon fontSize="small" />,
    description: 'Manage content tags and their status.',
    status: 'ready' as const,
  },
  {
    label: 'Categories',
    icon: <FolderIcon fontSize="small" />,
    description: 'Organise content into a category tree.',
    status: 'ready' as const,
  },
  {
    label: 'Articles',
    icon: <ArticleIcon fontSize="small" />,
    description: 'Create and publish articles.',
    status: 'soon' as const,
  },
  {
    label: 'Interview Questions',
    icon: <QuizIcon fontSize="small" />,
    description: 'Manage interview question bank.',
    status: 'soon' as const,
  },
];

export default function AdminDashboard(): JSX.Element {
  const adminUser = adminAuthService.getAdminUser();

  return (
    <Box sx={{ p: 3, maxWidth: 800 }}>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        Overview
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Welcome back{adminUser ? `, ${adminUser.username}` : ''}. Manage your platform content below.
      </Typography>

      <Divider sx={{ mb: 3 }} />

      <Stack spacing={1.5}>
        {FEATURE_CARDS.map(card => (
          <Paper key={card.label} sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <Box color="primary.main">{card.icon}</Box>
              <Box sx={{ flex: 1 }}>
                <Typography variant="body2" fontWeight={600}>
                  {card.label}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {card.description}
                </Typography>
              </Box>
              <Chip
                label={card.status === 'ready' ? 'Available' : 'Coming soon'}
                color={card.status === 'ready' ? 'success' : 'default'}
                size="small"
                variant="outlined"
              />
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Box>
  );
}
