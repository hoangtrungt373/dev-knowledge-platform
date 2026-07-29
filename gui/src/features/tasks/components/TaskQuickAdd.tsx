import { useRef, useState } from 'react';
import {
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import { CreateTaskPayload, TaskPriority } from '../types';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { formatDueDateLabel, isOverdue } from '../utils/taskBuckets';
import TaskOptionsMenu from './TaskOptionsMenu';
import DatePickerMenu from './DatePickerMenu';

interface Props {
  /** When the sidebar filter is a specific project, quick-added tasks are filed into it. */
  projectId?: number;
  onAdded: () => void;
}

const PRIORITY_ICON_COLOR: Record<TaskPriority, string> = {
  LOW: 'action.active',
  MEDIUM: 'info.main',
  HIGH: 'warning.main',
  URGENT: 'error.main',
};

export default function TaskQuickAdd({ projectId, onAdded }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const inputRef = useRef<HTMLInputElement>(null);

  const [title, setTitle] = useState('');
  const [dueDate, setDueDate] = useState<string | null>(null);
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [saving, setSaving] = useState(false);
  const [dateMenuAnchor, setDateMenuAnchor] = useState<HTMLElement | null>(null);
  const [moreMenuAnchor, setMoreMenuAnchor] = useState<HTMLElement | null>(null);

  const menuOpen = Boolean(dateMenuAnchor) || Boolean(moreMenuAnchor);

  const handleSubmit = async () => {
    const trimmed = title.trim();
    if (!trimmed || saving) return;
    setSaving(true);
    try {
      const payload: CreateTaskPayload = {
        title: trimmed,
        priority,
        dueDate: dueDate ?? undefined,
        projectId,
      };
      await taskApi.createTask(payload, showError);
      showSuccess('Task added');
      setTitle('');
      setDueDate(null);
      setPriority('MEDIUM');
      onAdded();
    } catch {
      // showError already called
    } finally {
      setSaving(false);
      // Keep focus on the title field so the user can immediately type the next task.
      inputRef.current?.focus();
    }
  };

  return (
    // mx: -1 alone (not px: 1 too) — verified against TaskRow.tsx via a real getBoundingClientRect
    // measurement (headless-Chrome CDP), not just source-reading. TaskRow's border-bottom is drawn
    // directly on its own bled Stack (mx: -1, px: 1 — the px pushes its *content*, checkbox/title,
    // back in so it stays aligned while only the background/border bleeds). This TextField has no
    // such split: its own outline *is* the thing that should bleed, so adding px: 1 here just insets
    // the visible border 8px inside where TaskRow's border-bottom actually ends — pairing mx: -1
    // with fullWidth (no px) lets the TextField itself fill the widened box, reaching the exact
    // same edges TaskRow's border does.
    <Stack sx={{ mb: 2, mx: -1 }}>
      <TextField
        inputRef={inputRef}
        placeholder="+ Add a task…"
        value={title}
        onChange={e => setTitle(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter' && !saving) handleSubmit(); }}
        size="small"
        fullWidth
        sx={{
          '& .task-quickadd-icon': { opacity: menuOpen ? 1 : 0 },
          '&:focus-within .task-quickadd-icon': { opacity: 1 },
        }}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              {dueDate ? (
                <Typography
                  variant="body2"
                  onClick={e => setDateMenuAnchor(e.currentTarget)}
                  sx={{
                    mr: 0.5,
                    cursor: 'pointer',
                    color: isOverdue(dueDate) ? 'error.main' : 'primary.main',
                  }}
                >
                  {formatDueDateLabel(dueDate)}
                </Typography>
              ) : (
                <Tooltip title="Set due date">
                  <IconButton
                    size="small"
                    className="task-quickadd-icon"
                    onClick={e => setDateMenuAnchor(e.currentTarget)}
                    sx={{ color: 'action.active' }}
                  >
                    <CalendarMonthIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              <IconButton
                size="small"
                className="task-quickadd-icon"
                onClick={e => setMoreMenuAnchor(e.currentTarget)}
                sx={{ color: PRIORITY_ICON_COLOR[priority] }}
              >
                <ExpandMoreIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          ),
        }}
      />

      <DatePickerMenu
        anchorEl={dateMenuAnchor}
        onClose={() => setDateMenuAnchor(null)}
        dueDate={dueDate}
        onDueDateChange={setDueDate}
      />

      <TaskOptionsMenu
        anchorEl={moreMenuAnchor}
        onClose={() => setMoreMenuAnchor(null)}
        priority={priority}
        onPriorityChange={setPriority}
      />
    </Stack>
  );
}
