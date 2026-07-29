import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { Project, Task } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TaskFormDialog from './TaskFormDialog';
import TaskRow, { TASK_ROW_ACTIONS_GUTTER_PX } from './TaskRow';

interface Props {
  task: Task | null;
  projects: Project[];
  onClose: () => void;
  /** Called after edit/delete so TasksPage can refetch the main list and re-sync this panel. */
  onChanged: () => void;
}

const PRIORITY_COLOR: Record<Task['priority'], 'default' | 'info' | 'warning' | 'error'> = {
  LOW: 'default',
  MEDIUM: 'info',
  HIGH: 'warning',
  URGENT: 'error',
};

function formatDueDate(iso: string | null): string | null {
  if (!iso) return null;
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export default function TaskDetailPanel({ task, projects, onClose, onChanged }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [loadingSubtasks, setLoadingSubtasks] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [addSubtaskOpen, setAddSubtaskOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // showSpinner is true only for the initial load below (task selection changes) — adding/
  // editing/deleting a subtask refetches quietly instead of blanking this section to a spinner.
  const fetchSubtasks = useCallback(async (opts?: { showSpinner?: boolean }) => {
    if (!task) return;
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoadingSubtasks(true);
    try {
      const data = await taskApi.listSubtasks(task.id, showError);
      setSubtasks(data);
    } finally {
      if (showSpinner) setLoadingSubtasks(false);
    }
  }, [task, showError]);

  useEffect(() => {
    if (task && !task.parentTaskId) fetchSubtasks(); else setSubtasks([]);
  }, [task, fetchSubtasks]);

  const refreshSubtasks = useCallback(() => fetchSubtasks({ showSpinner: false }), [fetchSubtasks]);

  if (!task) {
    return (
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', p: 3 }}>
        <Typography variant="body2" color="text.secondary">
          Select a task to see its details.
        </Typography>
      </Box>
    );
  }

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await taskApi.deleteTask(task.id, showError);
      showSuccess(`"${task.title}" deleted`);
      setDeleteOpen(false);
      onChanged();
      onClose();
    } catch {
      // showError already called
    } finally {
      setDeleting(false);
    }
  };

  const dueLabel = formatDueDate(task.dueDate);
  const projectName = projects.find(p => p.id === task.projectId)?.name;

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto', p: 2.5 }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between">
        <Typography variant="h6" fontWeight={700} sx={{ pr: 1 }}>
          {task.title}
        </Typography>
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: 'wrap' }}>
        <Chip
          label={task.priority.charAt(0) + task.priority.slice(1).toLowerCase()}
          color={PRIORITY_COLOR[task.priority]}
          size="small"
          variant="outlined"
        />
        {dueLabel && <Chip label={dueLabel} size="small" variant="outlined" />}
        {projectName && <Chip label={projectName} size="small" variant="outlined" />}
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>
        {task.description || 'No description.'}
      </Typography>

      <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
        <Button size="small" startIcon={<EditIcon fontSize="small" />} onClick={() => setEditOpen(true)}>
          Edit
        </Button>
        <Button size="small" color="error" startIcon={<DeleteIcon fontSize="small" />} onClick={() => setDeleteOpen(true)}>
          Delete
        </Button>
      </Stack>

      {!task.parentTaskId && (
        <>
          <Divider sx={{ my: 2.5 }} />

          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Typography variant="subtitle2" fontWeight={700}>Subtasks</Typography>
            <Tooltip title="Add subtask">
              <IconButton size="small" onClick={() => setAddSubtaskOpen(true)}>
                <AddIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>

          {loadingSubtasks ? (
            <Box sx={{ py: 2, display: 'flex', justifyContent: 'center' }}>
              <CircularProgress size={20} />
            </Box>
          ) : subtasks.length === 0 ? (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', py: 1 }}>
              No subtasks yet.
            </Typography>
          ) : (
            // pr reserves the same gutter TasksPage's list column does — this panel is the
            // rightmost column (no sibling to widen into), so the room for each row's "⋯" button
            // has to be carved out of this container's own padding instead.
            <Box sx={{ mt: 0.5, pr: `${TASK_ROW_ACTIONS_GUTTER_PX}px` }}>
              {subtasks.map(st => (
                <TaskRow key={st.id} task={st} onChanged={refreshSubtasks} />
              ))}
            </Box>
          )}
        </>
      )}

      <TaskFormDialog
        open={editOpen}
        task={task}
        projects={projects}
        onClose={() => setEditOpen(false)}
        onSaved={onChanged}
      />
      <TaskFormDialog
        open={addSubtaskOpen}
        task={null}
        parentTaskId={task.id}
        projects={projects}
        onClose={() => setAddSubtaskOpen(false)}
        onSaved={refreshSubtasks}
      />
      <ConfirmDialog
        open={deleteOpen}
        title="Delete Task"
        message={`Delete "${task.title}"? This cannot be undone.${!task.parentTaskId ? ' Any subtasks will be deleted too.' : ''}`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </Box>
  );
}
