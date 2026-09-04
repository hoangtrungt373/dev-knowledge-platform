import { Box, CircularProgress } from '@mui/material';

/** The whole-page "still loading" spinner — centered in a 50vh box. Extracted after the exact same
 * six-line block turned up byte-for-byte identical across seven different page components (see
 * gui/CLAUDE.md's ecommerce style-audit note); use this instead of re-inlining it. Not for a
 * scoped, in-place spinner (e.g. a results grid refreshing while its own filters stay visible) —
 * that's a different situation with a different (usually smaller, non-full-height) treatment. */
export default function FullPageLoader(): JSX.Element {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
      <CircularProgress />
    </Box>
  );
}
