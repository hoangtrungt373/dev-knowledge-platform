import { Chip, Stack, Tooltip, Typography } from '@mui/material';
import { ProblemTagSummary } from '../types';

interface Props {
  tags: ProblemTagSummary[];
  /** Show at most this many; the rest collapse into a "+N" chip whose tooltip names them. Omit to show all. */
  max?: number;
  /** What to render for no tags: a dash (table cells) or nothing at all. */
  emptyDash?: boolean;
}

/** A problem's tags as small chips — the list tables cap them at a few so rows keep one height. */
export default function TagChips({ tags, max, emptyDash = false }: Props): JSX.Element | null {
  if (tags.length === 0) {
    return emptyDash ? <Typography variant="body2" color="text.disabled">—</Typography> : null;
  }
  const shown = max === undefined ? tags : tags.slice(0, max);
  const hidden = tags.slice(shown.length);
  return (
    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
      {shown.map(t => <Chip key={t.id} size="small" label={t.name} />)}
      {hidden.length > 0 && (
        <Tooltip title={hidden.map(t => t.name).join(', ')}>
          <Chip size="small" variant="outlined" label={`+${hidden.length}`} />
        </Tooltip>
      )}
    </Stack>
  );
}
