import { useState } from 'react';
import {
  Box,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import InboxIcon from '@mui/icons-material/Inbox';
import TodayIcon from '@mui/icons-material/Today';
import DateRangeIcon from '@mui/icons-material/DateRange';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditIcon from '@mui/icons-material/Edit';
import ArchiveIcon from '@mui/icons-material/Archive';
import { Project, TaskFilter } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ProjectFormDialog from './ProjectFormDialog';

interface Props {
  /** All projects, owned/fetched once by TasksPage and shared with the dialogs/detail panel too —
   * avoids this component fetching its own separate copy of the same data. */
  projects: Project[];
  filter: TaskFilter;
  onFilterChange: (filter: TaskFilter) => void;
  /** Called after create/edit/archive so TasksPage can refetch the shared project list. */
  onProjectsChanged: () => void;
}

export default function TasksSidebar({ projects, filter, onFilterChange, onProjectsChanged }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [formOpen, setFormOpen] = useState(false);
  const [editProject, setEditProject] = useState<Project | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [menuProject, setMenuProject] = useState<Project | null>(null);

  const activeProjects = projects.filter(p => p.status === 'ACTIVE');

  const openCreate = () => { setEditProject(null); setFormOpen(true); };
  const closeMenu = () => { setMenuAnchor(null); setMenuProject(null); };

  const handleEdit = () => {
    if (menuProject) { setEditProject(menuProject); setFormOpen(true); }
    closeMenu();
  };

  const handleArchive = async () => {
    const project = menuProject;
    closeMenu();
    if (!project) return;
    try {
      await taskApi.archiveProject(project.id, showError);
      showSuccess(`"${project.name}" archived`);
      if (typeof filter === 'object' && filter.projectId === project.id) onFilterChange('all');
      onProjectsChanged();
    } catch {
      // showError already called
    }
  };

  return (
    // width/flexShrink used to size this against its flex-row siblings directly; now this is a
    // react-resizable-panels Panel's sole child, which already fixes this Box's width — height:
    // '100%' is what actually matters now, so it fills the Panel vertically too, and borderRight
    // is dropped in favor of the Panel's own ResizeHandle (TasksPage.tsx), which now serves as the
    // visual divider between this and the middle column.
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h6" fontWeight={700} sx={{ px: 2, pt: 2, pb: 1 }}>
        Tasks
      </Typography>

      <List dense sx={{ px: 1 }}>
        {/* 'all' is already the bucketed Overdue/Today/Upcoming/Completed dashboard and the
            filter's own initial/default state — this just gives it an explicit, always-visible
            entry point (Todoist calls the same "everything, sectioned" view "Inbox"), rather than
            it only being reachable by resetting some other filter. */}
        <ListItemButton
          selected={filter === 'all'}
          onClick={() => onFilterChange('all')}
          sx={{ borderRadius: 1 }}
        >
          <ListItemIcon sx={{ minWidth: 36 }}><InboxIcon fontSize="small" /></ListItemIcon>
          <ListItemText primary="Inbox" />
        </ListItemButton>
        <ListItemButton
          selected={filter === 'today'}
          onClick={() => onFilterChange('today')}
          sx={{ borderRadius: 1 }}
        >
          <ListItemIcon sx={{ minWidth: 36 }}><TodayIcon fontSize="small" /></ListItemIcon>
          <ListItemText primary="Today" />
        </ListItemButton>
        <ListItemButton
          selected={filter === 'week'}
          onClick={() => onFilterChange('week')}
          sx={{ borderRadius: 1 }}
        >
          <ListItemIcon sx={{ minWidth: 36 }}><DateRangeIcon fontSize="small" /></ListItemIcon>
          <ListItemText primary="This week" />
        </ListItemButton>
      </List>

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: 2, mt: 1 }}>
        <Typography variant="overline" color="text.secondary">Projects</Typography>
        <Tooltip title="New project">
          <IconButton size="small" onClick={openCreate}>
            <AddIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      <List dense sx={{ px: 1, flex: 1, overflowY: 'auto' }}>
        {activeProjects.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, display: 'block' }}>
            No projects yet.
          </Typography>
        ) : (
          activeProjects.map(p => (
            <ListItemButton
              key={p.id}
              selected={typeof filter === 'object' && filter.projectId === p.id}
              onClick={() => onFilterChange({ projectId: p.id })}
              sx={{ borderRadius: 1 }}
            >
              <ListItemText primary={p.name} primaryTypographyProps={{ noWrap: true }} />
              <IconButton
                size="small"
                onClick={e => { e.stopPropagation(); setMenuAnchor(e.currentTarget); setMenuProject(p); }}
              >
                <MoreVertIcon fontSize="small" />
              </IconButton>
            </ListItemButton>
          ))
        )}
      </List>

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        <MenuItem onClick={handleEdit}>
          <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleArchive}>
          <ListItemIcon><ArchiveIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Archive</ListItemText>
        </MenuItem>
      </Menu>

      <ProjectFormDialog
        open={formOpen}
        project={editProject}
        onClose={() => setFormOpen(false)}
        onSaved={onProjectsChanged}
      />
    </Box>
  );
}
