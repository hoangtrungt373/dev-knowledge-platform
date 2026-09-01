import { ReactNode } from 'react';
import { Box, Paper } from '@mui/material';

interface Props {
  children: ReactNode;
  maxWidth?: number;
}

/**
 * Shared centered-card layout for every standalone auth page (`Login.tsx`/`SignUp.tsx`/
 * `AdminLogin.tsx`) — the same `Box`+`Paper` wrapper each one used to duplicate on its own.
 */
export default function AuthCard({ children, maxWidth = 420 }: Props): JSX.Element {
  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="90vh" sx={{ px: 2, py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth }}>
        {children}
      </Paper>
    </Box>
  );
}
