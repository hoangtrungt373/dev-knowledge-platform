import { useState } from 'react';
import { Box, IconButton, Menu, Stack, TextField, Tooltip } from '@mui/material';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import WbSunnyIcon from '@mui/icons-material/WbSunny';
import WbTwilightIcon from '@mui/icons-material/WbTwilight';
import CalendarViewWeekIcon from '@mui/icons-material/CalendarViewWeek';
import { DATE_PRESETS, DATE_PRESET_LABEL, DatePreset, datePresetValue } from '../utils/taskBuckets';

interface Props {
  anchorEl: HTMLElement | null;
  onClose: () => void;
  /** Current due date, if any — used only to highlight the matching preset/Custom, same
   * `matchingPreset`/`isCustomDate` comparison TaskOptionsMenu's own Date section uses. */
  dueDate?: string | null;
  onDueDateChange: (dueDate: string | null) => void;
}

const DATE_PRESET_ICON: Record<DatePreset, JSX.Element> = {
  TODAY: <WbSunnyIcon fontSize="small" />,
  TOMORROW: <WbTwilightIcon fontSize="small" />,
  THIS_WEEK: <CalendarViewWeekIcon fontSize="small" />,
};

function toDateInputValue(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 10) : '';
}

/**
 * Date-presets-plus-Custom-field popover, shared by TaskQuickAdd (trigger is the calendar icon on
 * the quick-add input itself) and TaskRow (trigger is the due-date label). Deliberately its own
 * standalone Menu rather than folded into TaskOptionsMenu's Date section — that menu bundles
 * Priority/Status/Delete behind one "⋯" trigger, this is reachable directly without opening it.
 * Extracted out of TaskQuickAdd (which used to have this inline) once TaskRow needed the exact
 * same popover — two near-identical copies was the trigger to deduplicate, not a new pattern.
 */
export default function DatePickerMenu({ anchorEl, onClose, dueDate, onDueDateChange }: Props): JSX.Element {
  const [customDateOpen, setCustomDateOpen] = useState(false);

  const handleClose = () => { onClose(); setCustomDateOpen(false); };

  const dueDateOnly = dueDate ? new Date(dueDate).toDateString() : null;
  const matchingPreset = dueDateOnly
    ? DATE_PRESETS.find(preset => datePresetValue(preset).toDateString() === dueDateOnly)
    : undefined;
  const isCustomDate = Boolean(dueDateOnly) && !matchingPreset;

  return (
    <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
      <Box sx={{ px: 1.5, py: 1 }}>
        <Stack direction="row" spacing={0.5}>
          {DATE_PRESETS.map(preset => (
            <Tooltip key={preset} title={DATE_PRESET_LABEL[preset]}>
              <IconButton
                size="small"
                onClick={() => { onDueDateChange(datePresetValue(preset).toISOString()); handleClose(); }}
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
              onDueDateChange(e.target.value ? new Date(`${e.target.value}T00:00:00`).toISOString() : null);
              handleClose();
            }}
            sx={{ mt: 1 }}
          />
        )}
      </Box>
    </Menu>
  );
}
