import { useState } from 'react';
import {
  Box,
  Divider,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import FlagIcon from '@mui/icons-material/Flag';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import WbSunnyIcon from '@mui/icons-material/WbSunny';
import WbTwilightIcon from '@mui/icons-material/WbTwilight';
import CalendarViewWeekIcon from '@mui/icons-material/CalendarViewWeek';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import { TaskPriority, TaskStatus } from '../types';
import { DATE_PRESETS, DATE_PRESET_LABEL, DatePreset, datePresetValue } from '../utils/taskBuckets';

interface Props {
  anchorEl: HTMLElement | null;
  onClose: () => void;

  priority: TaskPriority;
  onPriorityChange: (priority: TaskPriority) => void;

  /** Omit both to hide the Date section entirely (e.g. TaskQuickAdd, which has its own separate
   * date-picker trigger and only uses this menu for Priority). */
  dueDate?: string | null;
  onDueDateChange?: (dueDate: string | null) => void;

  /** Omit both to hide the Status section (nothing to set a status on before a task exists). */
  status?: TaskStatus;
  onStatusChange?: (status: TaskStatus) => void;

  /** Omit to hide the Delete item (same reason as Status). */
  onDelete?: () => void;
}

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

const PRIORITY_LABEL: Record<TaskPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  URGENT: 'Urgent',
};

const PRIORITY_COLOR: Record<TaskPriority, string> = {
  LOW: 'action.disabled',
  MEDIUM: 'info.main',
  HIGH: 'warning.main',
  URGENT: 'error.main',
};

const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
};

const STATUS_ICON: Record<TaskStatus, JSX.Element> = {
  TODO: <RadioButtonUncheckedIcon fontSize="small" />,
  IN_PROGRESS: <AutorenewIcon fontSize="small" />,
  DONE: <CheckCircleIcon fontSize="small" />,
};

const STATUS_ICON_COLOR: Record<TaskStatus, string> = {
  TODO: 'action.active',
  IN_PROGRESS: 'info.main',
  DONE: 'success.main',
};

const DATE_PRESET_ICON: Record<DatePreset, JSX.Element> = {
  TODAY: <WbSunnyIcon fontSize="small" />,
  TOMORROW: <WbTwilightIcon fontSize="small" />,
  THIS_WEEK: <CalendarViewWeekIcon fontSize="small" />,
};

function toDateInputValue(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 10) : '';
}

/**
 * Shared "⋯" options menu content — a single Box with inline icon rows for Date/Priority/Status
 * plus a Delete item, reused by both TaskRow (full menu, backed by an existing task — every
 * section present) and TaskQuickAdd (Priority only, backed by pending local state before the task
 * exists — Date/Status/Delete omitted since they either don't apply yet or have their own
 * separate control). Fully controlled: the caller owns the actual values and what happens when
 * they change (an immediate API call for TaskRow, just local state for TaskQuickAdd).
 */
export default function TaskOptionsMenu({
  anchorEl,
  onClose,
  priority,
  onPriorityChange,
  dueDate,
  onDueDateChange,
  status,
  onStatusChange,
  onDelete,
}: Props): JSX.Element {
  const [customDateOpen, setCustomDateOpen] = useState(false);

  const showDate = Boolean(onDueDateChange);
  const showStatus = Boolean(onStatusChange && status);

  const handleClose = () => { onClose(); setCustomDateOpen(false); };

  const dueDateOnly = dueDate ? new Date(dueDate).toDateString() : null;
  const matchingPreset = dueDateOnly
    ? DATE_PRESETS.find(preset => datePresetValue(preset).toDateString() === dueDateOnly)
    : undefined;
  const isCustomDate = Boolean(dueDateOnly) && !matchingPreset;

  return (
    <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
      <Box sx={{ px: 1.5, py: 1 }}>
        {showDate && (
          <>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              Date
            </Typography>
            <Stack direction="row" spacing={0.5}>
              {DATE_PRESETS.map(preset => (
                <Tooltip key={preset} title={DATE_PRESET_LABEL[preset]}>
                  <IconButton
                    size="small"
                    onClick={() => { onDueDateChange!(datePresetValue(preset).toISOString()); handleClose(); }}
                    sx={{ borderRadius: 1, bgcolor: matchingPreset === preset ? 'action.selected' : 'transparent' }}
                  >
                    {DATE_PRESET_ICON[preset]}
                  </IconButton>
                </Tooltip>
              ))}
              <Tooltip title="Custom">
                <IconButton
                  size="small"
                  onClick={() => setCustomDateOpen(v => !v)}
                  sx={{ borderRadius: 1, bgcolor: isCustomDate || customDateOpen ? 'action.selected' : 'transparent' }}
                >
                  <CalendarMonthIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
            {customDateOpen && (
              <TextField
                type="date"
                size="small"
                autoFocus
                fullWidth
                defaultValue={toDateInputValue(dueDate)}
                onChange={e => {
                  onDueDateChange!(e.target.value ? new Date(`${e.target.value}T00:00:00`).toISOString() : null);
                  handleClose();
                }}
                sx={{ mt: 1 }}
              />
            )}
          </>
        )}

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: showDate ? 1.5 : 0, mb: 0.5 }}>
          Priority
        </Typography>
        <Stack direction="row" spacing={0.5}>
          {PRIORITIES.map(p => (
            <Tooltip key={p} title={PRIORITY_LABEL[p]}>
              <IconButton
                size="small"
                onClick={() => { onPriorityChange(p); handleClose(); }}
                sx={{ borderRadius: 1, bgcolor: p === priority ? 'action.selected' : 'transparent' }}
              >
                <FlagIcon fontSize="small" sx={{ color: PRIORITY_COLOR[p] }} />
              </IconButton>
            </Tooltip>
          ))}
        </Stack>

        {showStatus && (
          <>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, mb: 0.5 }}>
              Status
            </Typography>
            <Stack direction="row" spacing={0.5}>
              {STATUSES.map(s => (
                <Tooltip key={s} title={STATUS_LABEL[s]}>
                  <IconButton
                    size="small"
                    onClick={() => { onStatusChange!(s); handleClose(); }}
                    sx={{
                      borderRadius: 1,
                      bgcolor: s === status ? 'action.selected' : 'transparent',
                      color: STATUS_ICON_COLOR[s],
                    }}
                  >
                    {STATUS_ICON[s]}
                  </IconButton>
                </Tooltip>
              ))}
            </Stack>
          </>
        )}
      </Box>

      {onDelete && (
        <>
          <Divider />
          <MenuItem onClick={() => { handleClose(); onDelete(); }} sx={{ color: 'error.main' }}>
            <ListItemIcon><DeleteIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Delete</ListItemText>
          </MenuItem>
        </>
      )}
    </Menu>
  );
}
