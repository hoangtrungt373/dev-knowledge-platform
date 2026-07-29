import { ReactNode, useEffect, useState } from 'react';
import { Box, Checkbox, IconButton, Stack, TextField, Typography } from '@mui/material';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import { Task, TaskPriority, TaskStatus, UpdateTaskPayload } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import TaskOptionsMenu from './TaskOptionsMenu';
import DatePickerMenu from './DatePickerMenu';
import { formatDueDateLabel, isOverdue } from '../utils/taskBuckets';

// Exported so SortableTaskRow can type its own props as Omit<TaskRowProps, 'dragHandle'> instead
// of re-declaring this list — it constructs dragHandle itself, so accepting it as a prop from its
// own caller would be a foot-gun.
export interface TaskRowProps {
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
  /** Rendered before the checkbox, e.g. SortableTaskRow's drag-handle IconButton — TaskRow itself
   * has no @dnd-kit dependency; the handle is fully owned/positioned (as an absolutely-positioned
   * left-gutter element, mirroring the "⋯" button's own right-gutter treatment) by the caller, so
   * rows that don't support reordering (subtasks inside TaskDetailPanel) just omit this prop and
   * carry no such reserved space at all. */
  dragHandle?: ReactNode;
}

// Width reserved for the "⋯" button and (via SortableTaskRow) the drag handle, once each is
// pulled out of the row's flex flow (see the IconButton below, and SortableTaskRow's own) —
// exported so every container that lays out TaskRow reserves matching space on the relevant side
// (TasksPage's list column gets both a wider `pr` for "⋯" and a wider `pl` for the drag handle;
// TaskDetailPanel's subtask list only needs the `pr` side, since subtask rows never render a drag
// handle) instead of either icon overlapping row content or a sibling column. Both icons are also
// shrunk below MUI's own smallest fontSize="small" preset (down to a plain 18px glyph, `p: '4px'`
// instead of size="small"'s default ~5px) — a narrower gutter alone wouldn't fit a still
// default-sized button without clipping it.
export const TASK_ROW_ACTIONS_GUTTER_PX = 28;

// Drives the checkbox's unchecked-state color — 'default' isn't a real palette key, so LOW gets
// a real (subtle) path rather than reusing a Chip color name.
const PRIORITY_COLOR: Record<TaskPriority, string> = {
  LOW: 'action.disabled',
  MEDIUM: 'info.main',
  HIGH: 'warning.main',
  URGENT: 'error.main',
};

export default function TaskRow({ task, onSelect, onChanged, selected = false, dragHandle }: TaskRowProps): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [dateMenuAnchor, setDateMenuAnchor] = useState<HTMLElement | null>(null);
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

  const startEditingTitle = () => {
    setTitleDraft(displayedTitle);
    setEditingTitle(true);
  };

  const closeMenu = () => setMenuAnchor(null);
  const closeDateMenu = () => setDateMenuAnchor(null);

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
        // Selecting a row both opens it in TaskDetailPanel (via onSelect) and drops straight into
        // title-rename mode — the title TextField's autoFocus then lands keyboard focus there,
        // so a single click both selects and lines up an edit without a second click on the
        // title text specifically.
        onClick={onSelect ? () => { onSelect(task); startEditingTitle(); } : undefined}
        sx={{
          position: 'relative',
          minHeight: 37,
          py: 0,
          px: 1,
          mx: -1,
          borderRadius: 1,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: selected ? 'action.selected' : 'transparent',
          '&:hover': { bgcolor: selected ? 'action.selected' : 'action.hover' },
          '&:hover .task-row-more, &:hover .task-row-drag-handle, &:focus-within .task-row-more, &:focus-within .task-row-drag-handle': { opacity: 1 },
        }}
      >
        {dragHandle}

        <Checkbox
          size="small"
          checked={task.status === 'DONE'}
          onClick={e => e.stopPropagation()}
          onChange={e => handleToggleDone(e.target.checked)}
          icon={<CheckBoxOutlineBlankIcon sx={{ color: PRIORITY_COLOR[task.priority] }} />}
          checkedIcon={<CheckBoxIcon sx={{ color: 'success.main' }} />}
          sx={{ p: '8px' }}
        />

        {/* display:flex + alignItems:center is load-bearing, not decorative: Typography is
            plain inline content, subject to half-leading (baseline alignment against this box's
            ambient line-height "strut" pushes it down a couple px); TextField's root is
            display:inline-flex, an atomic box that gets no half-leading and sits flush at the
            top instead. Matching font-size/line-height between the two can't fix that — it's a
            different positioning algorithm, not a metrics mismatch. Making this box a flex
            container puts both under flexbox alignment instead, which treats them identically
            regardless of which one is mounted. Verified pixel-for-pixel via a headless-Chrome
            CDP measurement (getBoundingClientRect on both states) — before this, the two leaf
            elements differed by exactly 2px top/bottom despite identical height. */}
        <Box sx={{ flexGrow: 1, minWidth: 0, display: 'flex', alignItems: 'center' }}>
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
                // .MuiInputBase-root/-input default to theme.typography.body1 (16px) — without
                // this, the title's font would visibly grow when entering edit mode. Matches the
                // Typography sibling's variant="body2" below. (The vertical-position jump this
                // was originally written to fix turned out to be a different bug — see the
                // comment on the parent Box — but the font-size mismatch these override is real
                // regardless, so they stay.)
                '& .MuiInputBase-root': {
                  fontSize: theme.typography.body2.fontSize,
                  lineHeight: theme.typography.body2.lineHeight,
                },
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
              onClick={e => { e.stopPropagation(); startEditingTitle(); }}
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
          <Typography
            variant="body2"
            onClick={e => { e.stopPropagation(); setDateMenuAnchor(e.currentTarget); }}
            sx={{ color: dueOverdue ? 'error.main' : 'primary.main', cursor: 'pointer' }}
          >
            {dueLabel}
          </Typography>
        )}

        {/* Taken out of the row's flex flow entirely (position: absolute against the Stack's own
            position: relative above) rather than just hidden via opacity — an in-flow hidden
            button still reserves its width, which was pushing the due date left of the row's true
            right edge even while invisible. Positioned into the dedicated gutter that
            TASK_ROW_ACTIONS_GUTTER_PX reserves in this row's container (TasksPage's list column /
            TaskDetailPanel's subtask list), so it never overlaps the due date label next to it. */}
        <IconButton
          className="task-row-more"
          onClick={e => { e.stopPropagation(); setMenuAnchor(e.currentTarget); }}
          sx={{
            position: 'absolute',
            top: '50%',
            right: -(TASK_ROW_ACTIONS_GUTTER_PX - 4),
            transform: 'translateY(-50%)',
            p: '4px',
            opacity: menuAnchor ? 1 : 0,
            transition: 'opacity 0.1s',
          }}
        >
          <MoreHorizIcon sx={{ fontSize: 18 }} />
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
        {/* Reachable two ways now: this direct click on the due-date label, or Date inside the
            "⋯" menu above (TaskOptionsMenu) — both call the same handleDueDateChange, so neither
            path can drift out of sync with the other. Only rendered when dueLabel is set (mirrors
            the Typography above) since there's nothing to click when no due date exists yet —
            setting one for the first time still goes through the "⋯" menu's Date section. */}
        {dueLabel && (
          <DatePickerMenu
            anchorEl={dateMenuAnchor}
            onClose={closeDateMenu}
            dueDate={task.dueDate}
            onDueDateChange={handleDueDateChange}
          />
        )}
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
