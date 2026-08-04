import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Checkbox,
  CircularProgress,
  Divider,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import DeleteIcon from '@mui/icons-material/Delete';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import FlagIcon from '@mui/icons-material/Flag';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import { Task, TaskPriority, TaskStatus, UpdateTaskPayload } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TaskOptionsMenu from './TaskOptionsMenu';
import DatePickerMenu from './DatePickerMenu';
import TaskRow, { TASK_ROW_ACTIONS_GUTTER_PX } from './TaskRow';
import { formatDueDateLabel, isOverdue } from '../utils/taskBuckets';

interface Props {
  task: Task | null;
  onClose: () => void;
  /** Called after any mutation so TasksPage can refetch the main list and re-sync this panel. */
  onChanged: () => void;
}

// Matches TaskOptionsMenu's own map — kept as a separate copy rather than exported/shared, same
// as every other component in this feature that needs this exact mapping (TaskRow, TaskQuickAdd).
const PRIORITY_COLOR: Record<TaskPriority, string> = {
  LOW: 'action.disabled',
  MEDIUM: 'info.main',
  HIGH: 'warning.main',
  URGENT: 'error.main',
};

const PRIORITY_LABEL: Record<TaskPriority, string> = {
  LOW: 'Low priority',
  MEDIUM: 'Medium priority',
  HIGH: 'High priority',
  URGENT: 'Urgent priority',
};

/**
 * Task detail panel — checkbox / due date / priority header row, then an inline-editable title
 * and description. No separate "Edit" dialog (see TaskFormDialog's removal, docs/CHANGELOG.md):
 * every field here is editable directly in place, the same click-to-edit pattern TaskRow.tsx
 * already established for its own title.
 *
 * Deliberately out of scope for this pass (see docs/CHANGELOG.md): reassigning a task's project
 * after creation, and adding a new subtask — both used to go through TaskFormDialog, which no
 * longer exists. The subtask *list* below is unaffected (it doesn't depend on TaskFormDialog to
 * render), only creating a new one is currently unreachable from this panel.
 */
export default function TaskDetailPanel({ task, onClose, onChanged }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [loadingSubtasks, setLoadingSubtasks] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [dateMenuAnchor, setDateMenuAnchor] = useState<HTMLElement | null>(null);
  const [priorityMenuAnchor, setPriorityMenuAnchor] = useState<HTMLElement | null>(null);

  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState('');
  const [editingDescription, setEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState('');
  // Bridges the gap between committing an edit and onChanged()'s refetch landing — without these,
  // switching back to the Typography would render the prop's stale pre-edit value for one frame, a
  // visible blink back to the old value before the refetch catches up. Same pattern TaskRow.tsx
  // uses for its own title edit, applied here to both title and description since both toggle
  // between a Typography and a TextField the same way.
  const [optimisticTitle, setOptimisticTitle] = useState<string | null>(null);
  const [optimisticDescription, setOptimisticDescription] = useState<string | null>(null);

  useEffect(() => {
    if (!task) return;
    if (optimisticTitle !== null && task.title === optimisticTitle) setOptimisticTitle(null);
    if (optimisticDescription !== null && task.description === optimisticDescription) setOptimisticDescription(null);
    // Only re-check against the freshest task, not every optimistic-state change — re-running on
    // optimisticTitle/optimisticDescription themselves would also fire right when they're first
    // set, before any refetch could possibly have landed yet.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task]);

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
      // flex: 1 used to size this against its flex-row siblings directly; now this is a
      // react-resizable-panels Panel's sole child, which already fixes this Box's width — height:
      // '100%' is what actually matters now, so it fills the Panel vertically too.
      <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 3 }}>
        <Typography variant="body2" color="text.secondary">
          Select a task to see its details.
        </Typography>
      </Box>
    );
  }

  const displayedTitle = optimisticTitle ?? task.title;
  const displayedDescription = optimisticDescription ?? task.description;

  const startEditingTitle = () => { setTitleDraft(displayedTitle); setEditingTitle(true); };
  const startEditingDescription = () => { setDescriptionDraft(displayedDescription ?? ''); setEditingDescription(true); };

  // Every mutation below resends the task's current field values plus the one changed field —
  // there's no dedicated per-field endpoint (UpdateTaskPayload/TaskServiceImpl.updateTask fully
  // replace mutable fields, see task-service/CLAUDE.md), same approach TaskRow.tsx uses for its
  // own priority/due-date/title changes.
  const basePayload = (): UpdateTaskPayload => ({
    title: task.title,
    description: task.description,
    projectId: task.projectId,
    priority: task.priority,
    dueDate: task.dueDate,
    parentTaskId: task.parentTaskId,
  });

  const commitTitleEdit = async () => {
    const trimmed = titleDraft.trim();
    setEditingTitle(false);
    if (!trimmed || trimmed === displayedTitle) return;
    setOptimisticTitle(trimmed);
    try {
      await taskApi.updateTask(task.id, { ...basePayload(), title: trimmed }, showError);
      onChanged();
    } catch {
      setOptimisticTitle(null);
    }
  };

  const commitDescriptionEdit = async () => {
    const trimmed = descriptionDraft.trim();
    setEditingDescription(false);
    const normalized = trimmed || null;
    if (normalized === displayedDescription) return;
    setOptimisticDescription(normalized);
    try {
      await taskApi.updateTask(task.id, { ...basePayload(), description: normalized }, showError);
      onChanged();
    } catch {
      setOptimisticDescription(null);
    }
  };

  const handleToggleDone = async (checked: boolean) => {
    const newStatus: TaskStatus = checked ? 'DONE' : 'TODO';
    try {
      await taskApi.changeTaskStatus(task.id, newStatus, showError);
      onChanged();
    } catch {
      // showError already called
    }
  };

  const handleDueDateChange = async (dueDate: string | null) => {
    if (dueDate === task.dueDate) return;
    try {
      await taskApi.updateTask(task.id, { ...basePayload(), dueDate }, showError);
      onChanged();
    } catch {
      // showError already called
    }
  };

  const handlePriorityChange = async (priority: TaskPriority) => {
    if (priority === task.priority) return;
    try {
      await taskApi.updateTask(task.id, { ...basePayload(), priority }, showError);
      onChanged();
    } catch {
      // showError already called
    }
  };

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

  const dueLabel = task.dueDate ? formatDueDateLabel(task.dueDate) : null;
  const dueOverdue = task.status !== 'DONE' && task.dueDate ? isOverdue(task.dueDate) : false;
  const dueColor = task.dueDate ? (dueOverdue ? 'error.main' : 'primary.main') : 'text.secondary';

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflowY: 'auto', p: 2.5 }}>
      <Stack direction="row" justifyContent="flex-end" spacing={0.5}>
        <IconButton size="small" onClick={() => setDeleteOpen(true)}>
          <DeleteIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Stack direction="row" alignItems="center" spacing={1.5}>
        <Checkbox
          checked={task.status === 'DONE'}
          onChange={e => handleToggleDone(e.target.checked)}
          icon={<CheckBoxOutlineBlankIcon />}
          checkedIcon={<CheckBoxIcon sx={{ color: 'success.main' }} />}
        />

        <Divider orientation="vertical" flexItem />

        <Stack
          direction="row"
          alignItems="center"
          spacing={0.5}
          onClick={e => setDateMenuAnchor(e.currentTarget)}
          sx={{ cursor: 'pointer', color: dueColor }}
        >
          <CalendarMonthIcon fontSize="small" sx={{ color: 'inherit' }} />
          <Typography variant="body2" sx={{ color: 'inherit' }}>
            {dueLabel ?? 'Add due date'}
          </Typography>
        </Stack>

        <Box sx={{ flexGrow: 1 }} />

        <Tooltip title={PRIORITY_LABEL[task.priority]}>
          <IconButton size="small" onClick={e => setPriorityMenuAnchor(e.currentTarget)}>
            <FlagIcon sx={{ color: PRIORITY_COLOR[task.priority] }} />
          </IconButton>
        </Tooltip>
      </Stack>

      <Divider sx={{ my: 2 }} />

      {editingTitle ? (
        <TextField
          variant="standard"
          autoFocus
          fullWidth
          value={titleDraft}
          onChange={e => setTitleDraft(e.target.value)}
          onBlur={commitTitleEdit}
          onKeyDown={e => {
            if (e.key === 'Enter') { e.preventDefault(); commitTitleEdit(); }
            if (e.key === 'Escape') { e.preventDefault(); setEditingTitle(false); }
          }}
          InputProps={{ disableUnderline: true }}
          sx={theme => ({
            '& .MuiInputBase-input': {
              fontSize: theme.typography.h6.fontSize,
              fontWeight: 700,
              padding: 0,
            },
          })}
        />
      ) : (
        <Typography
          variant="h6"
          fontWeight={700}
          onClick={startEditingTitle}
          sx={{
            cursor: 'text',
            textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
            color: task.status === 'DONE' ? 'text.disabled' : 'text.primary',
          }}
        >
          {displayedTitle}
        </Typography>
      )}

      {editingDescription ? (
        <TextField
          variant="standard"
          autoFocus
          fullWidth
          multiline
          minRows={2}
          value={descriptionDraft}
          onChange={e => setDescriptionDraft(e.target.value)}
          onBlur={commitDescriptionEdit}
          onKeyDown={e => {
            // Enter is a real newline in a multiline field — only Escape cancels here, unlike
            // the single-line title above.
            if (e.key === 'Escape') { e.preventDefault(); setEditingDescription(false); }
          }}
          InputProps={{ disableUnderline: true }}
          sx={{
            mt: 1.5,
            '& .MuiInputBase-input': {
              fontSize: '0.8125rem',
              padding: 0,
            },
          }}
        />
      ) : (
        <Typography
          variant="body2"
          color={displayedDescription ? 'text.secondary' : 'text.disabled'}
          onClick={startEditingDescription}
          sx={{ mt: 1.5, whiteSpace: 'pre-wrap', cursor: 'text' }}
        >
          {displayedDescription || 'Add a description…'}
        </Typography>
      )}

      {!task.parentTaskId && (
        <>
          <Divider sx={{ my: 2.5 }} />

          <Typography variant="subtitle2" fontWeight={700}>Subtasks</Typography>

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

      <DatePickerMenu
        anchorEl={dateMenuAnchor}
        onClose={() => setDateMenuAnchor(null)}
        dueDate={task.dueDate}
        onDueDateChange={handleDueDateChange}
      />
      <TaskOptionsMenu
        anchorEl={priorityMenuAnchor}
        onClose={() => setPriorityMenuAnchor(null)}
        priority={task.priority}
        onPriorityChange={handlePriorityChange}
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
