import { Checkbox, ListItemText, MenuItem, Select, SxProps, Theme } from '@mui/material';
import { ProblemTagSummary } from '../types';

interface Props {
  tags: ProblemTagSummary[];
  selectedIds: number[];
  onChange: (ids: number[]) => void;
  /** Shown while nothing is selected ("All tags", "All topics"). */
  allLabel: string;
  sx?: SxProps<Theme>;
}

/**
 * A multi-select of tags with a checkbox per option. Matches problems with ANY selected tag — the
 * backend's OR semantics for a repeated `tagIds` param. Used by the admin and the learner list.
 */
export default function TagFilterSelect({ tags, selectedIds, onChange, allLabel, sx }: Props): JSX.Element {
  const nameOf = (id: number) => tags.find(t => t.id === id)?.name ?? `#${id}`;
  return (
    <Select
      multiple
      displayEmpty
      size="small"
      value={selectedIds}
      // MUI hands back a string only for an autofilled native select, which this isn't.
      onChange={e => onChange(typeof e.target.value === 'string' ? [] : e.target.value)}
      renderValue={selected => (selected.length === 0 ? allLabel : selected.map(nameOf).join(', '))}
      sx={sx}
    >
      {tags.map(t => (
        <MenuItem key={t.id} value={t.id}>
          <Checkbox size="small" checked={selectedIds.includes(t.id)} sx={{ p: 0.5, mr: 1 }} />
          <ListItemText primary={t.name} />
        </MenuItem>
      ))}
    </Select>
  );
}
