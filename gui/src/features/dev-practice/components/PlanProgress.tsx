import { Box, LinearProgress, Typography } from '@mui/material';

interface PlanProgressProps {
  solved: number;
  total: number;
  /** Shown with the bar; logged-out visitors get just the size, since they have no progress. */
  showProgress: boolean;
}

/**
 * "x / y solved" with a thin bar — for a plan card, a plan's header and each section. Logged out,
 * only the size ("y problems"). A plan whose problems are all still drafts reads "No problems yet".
 */
export default function PlanProgress({ solved, total, showProgress }: PlanProgressProps): JSX.Element {
  if (total === 0) {
    return <Typography variant="caption" color="text.secondary">No problems yet</Typography>;
  }
  if (!showProgress) {
    return (
      <Typography variant="caption" color="text.secondary">
        {total} problem{total !== 1 ? 's' : ''}
      </Typography>
    );
  }
  const done = solved === total;
  return (
    <Box sx={{ minWidth: 140 }}>
      <Typography variant="caption" color={done ? 'success.main' : 'text.secondary'} fontWeight={done ? 700 : 400}>
        {solved} / {total} solved
      </Typography>
      <LinearProgress
        variant="determinate"
        value={(solved / total) * 100}
        color={done ? 'success' : 'primary'}
        sx={{ height: 6, borderRadius: 3, mt: 0.25 }}
        aria-label={`${solved} of ${total} solved`}
      />
    </Box>
  );
}
