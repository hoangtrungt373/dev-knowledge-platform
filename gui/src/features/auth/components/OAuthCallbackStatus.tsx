import { Alert, Box, CircularProgress, Typography } from '@mui/material';

interface Props {
  error: string | null;
  errorTitle: string;
  loadingLabel: string;
  redirectingLabel: string;
}

/**
 * Shared loading/error UI for both `AuthCallback.tsx` and `AdminAuthCallback.tsx` — renders the
 * result of {@link "../hooks/useOAuthCallback"}, which owns the actual exchange/redirect flow.
 */
export default function OAuthCallbackStatus({ error, errorTitle, loadingLabel, redirectingLabel }: Props): JSX.Element {
  if (error) {
    return (
      <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh" sx={{ px: 2 }}>
        <Alert severity="error" sx={{ mb: 2, maxWidth: 500 }}>
          <Typography variant="h6">{errorTitle}</Typography>
          <Typography variant="body2">{error}</Typography>
        </Alert>
        <Typography variant="body2" color="text.secondary">
          {redirectingLabel}
        </Typography>
      </Box>
    );
  }

  return (
    <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="60vh">
      <CircularProgress size={60} />
      <Typography variant="body1" sx={{ mt: 3 }}>
        {loadingLabel}
      </Typography>
    </Box>
  );
}
