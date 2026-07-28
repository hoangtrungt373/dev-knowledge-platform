import { useEffect, useState } from 'react';
import { Box, Checkbox, IconButton, Stack, TextField, Typography } from '@mui/material';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import { Task, TaskPriority, TaskStatus, UpdateTaskPayload } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TaskOptionsMenu from './TaskOptionsMenu';
import { formatDueDateLabel, isOverdue } from '../utils/taskBuckets';

interface Props {
  task: Task;
  /** Present for top-level rows in the main task list — clicking the title opens the detail
   * panel. Absent when this row renders a subtask inside TaskDetailPanel, where the title is
   * just a label (subtasks aren't independently selectable — capped at one level deep). */
  onSelect?: (task: Task) => void;
  /** Called after any mutation to this row (priority/status change, delete). */
  onChanged: () => void;
  /** True when this row is the task currently open in TaskDetailPanel — gives it a persistent
   * darker background so it stays visually distinct even when the mouse isn't over it. */
  selected?: boolean;
}

// Drives the checkbox's unchecked-state color — 'default' isn't a real palette key, so LOW gets
// a real (subtle) path rather than reusing a Chip color name.
const PRIORITY_COLOR: Record<TaskPriority, string> = {
  LOW: 'action.disabled',
  MEDIUM: 'info.main',
  HIGH: 'warning.main',
  URGENT: 'error.main',
};

export default function TaskRow({ task, onSelect, onChanged, selected = false }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState(task.title);
  // Bridges the gap between committing a title edit and onChanged()'s refetch landing — without
  // it, switching back to the Typography would render task.title (still the pre-edit value from
  // props) for one frame, a visible blink back to the old title before the refetch catches up.
  const [optimisticTitle, setOptimisticTitle] = useState<string | null>(null);

  useEffect(() => {
    if (optimisticTitle !== null && task.title === optimisticTitle) setOptimisticTitle(null);
  }, [task.title, optimisticTitle]);

  const displayedTitle = optimisticTitle ?? task.title;

  const closeMenu = () => setMenuAnchor(null);

  const commitTitleEdit = async () => {
    const trimmed = titleDraft.trim();
    setEditingTitle(false);
    if (!trimmed || trimmed === displayedTitle) return;
    setOptimisticTitle(trimmed);
    try {
      // Same full-replace constraint as handlePriorityChange/handleDueDateChange — no dedicated
      // "rename task" endpoint either.
      const payload: UpdateTaskPayload = {
        title: trimmed,
        description: task.description,
        projectId: task.projectId,
        priority: task.priority,
        dueDate: task.dueDate,
        parentTaskId: task.parentTaskId,
      };
      await taskApi.updateTask(task.id, payload, showError);
      onChanged();
    } catch {
      setOptimisticTitle(null);
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

  const handleStatusChange = async (status: TaskStatus) => {
    if (status === task.status) return;
    try {
      await taskApi.changeTaskStatus(task.id, status, showError);
      onChanged();
    } catch {
      // showError already called
    }
  };

  const handlePriorityChange = async (priority: TaskPriority) => {
    if (priority === task.priority) return;
    try {
      // No dedicated "change priority" endpoint — updateTask fully replaces mutable fields, so
      // every other field is resent unchanged (same approach TaskFormDialog uses on edit).
      const payload: UpdateTaskPayload = {
        title: task.title,
        description: task.description,
        projectId: task.projectId,
        priority,
        dueDate: task.dueDate,
        parentTaskId: task.parentTaskId,
      };
      await taskApi.updateTask(task.id, payload, showError);
      onChanged();
    } catch {
      // showError already called
    }
  };

  const handleDueDateChange = async (dueDate: string | null) => {
    if (dueDate === task.dueDate) return;
    try {
      // Same full-replace constraint as handlePriorityChange — no dedicated "change due date"
      // endpoint either.
      const payload: UpdateTaskPayload = {
        title: task.title,
        description: task.description,
        projectId: task.projectId,
        priority: task.priority,
        dueDate,
        parentTaskId: task.parentTaskId,
      };
      await taskApi.updateTask(task.id, payload, showError);
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
    } catch {
      // showError already called
    } finally {
      setDeleting(false);
    }
  };

  const dueLabel = task.dueDate ? formatDueDateLabel(task.dueDate) : null;
  const dueOverdue = task.status !== 'DONE' && task.dueDate ? isOverdue(task.dueDate) : false;

  return (
    <Box>
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        onClick={onSelect ? () => onSelect(task) : undefined}
        sx={{
          minHeight: 37,
          py: 0,
          px: 1,
          mx: -1,
          borderRadius: 1,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: selected ? 'action.selected' : 'transparent',
          '&:hover': { bgcolor: selected ? 'action.selected' : 'action.hover' },
          '&:hover .task-row-more, &:focus-within .task-row-more': { opacity: 1 },
        }}
      >
        <Checkbox
          size="small"
          checked={task.status === 'DONE'}
          onClick={e => e.stopPropagation()}
          onChange={e => handleToggleDone(e.target.checked)}
          icon={<CheckBoxOutlineBlankIcon sx={{ color: PRIORITY_COLOR[task.priority] }} />}
          checkedIcon={<CheckBoxIcon sx={{ color: 'success.main' }} />}
          sx={{ p: '8px' }}
        />

        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          {editingTitle ? (
            <TextField
              size="small"
              variant="standard"
              autoFocus
              value={titleDraft}
              onChange={e => setTitleDraft(e.target.value)}
              onClick={e => e.stopPropagation()}
              onBlur={commitTitleEdit}
              onKeyDown={e => {
                if (e.key === 'Enter') { e.preventDefault(); commitTitleEdit(); }
                if (e.key === 'Escape') { e.preventDefault(); setEditingTitle(false); }
              }}
              InputProps={{ disableUnderline: true }}
              sx={theme => ({
                // Deliberately not flexGrow/fullWidth — leaving blank space in the flexGrow: 1
                // parent Box (which has no onClick of its own) means a click there still bubbles
                // to the row's onClick and selects it, even while this row's title is mid-edit.
                maxWidth: 240,
                '& .MuiInputBase-input': {
                  fontSize: theme.typography.body2.fontSize,
                  lineHeight: theme.typography.body2.lineHeight,
                  padding: 0,
                },
              })}
            />
          ) : (
            <Typography
              variant="body2"
              onClick={e => { e.stopPropagation(); setTitleDraft(displayedTitle); setEditingTitle(true); }}
              sx={{
                textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
                color: task.status === 'DONE' ? 'text.disabled' : 'text.primary',
                cursor: 'text',
                display: 'inline-block',
              }}
            >
              {displayedTitle}
            </Typography>
          )}
        </Box>

        {dueLabel && (
          <Typography variant="body2" sx={{ color: dueOverdue ? 'error.main' : 'primary.main' }}>
            {dueLabel}
          </Typography>
        )}

        <IconButton
          size="small"
          className="task-row-more"
          onClick={e => { e.stopPropagation(); setMenuAnchor(e.currentTarget); }}
          sx={{ opacity: menuAnchor ? 1 : 0, transition: 'opacity 0.1s' }}
        >
          <MoreHorizIcon fontSize="small" />
        </IconButton>
        <TaskOptionsMenu
          anchorEl={menuAnchor}
          onClose={closeMenu}
          priority={task.priority}
          onPriorityChange={handlePriorityChange}
          dueDate={task.dueDate}
          onDueDateChange={handleDueDateChange}
          status={task.status}
          onStatusChange={handleStatusChange}
          onDelete={() => setDeleteOpen(true)}
        />
      </Stack>

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
