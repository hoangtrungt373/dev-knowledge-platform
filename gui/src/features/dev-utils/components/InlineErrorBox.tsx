import { Box, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { DevUtilError } from '../utils/errorFormatting';

interface InlineErrorBoxProps {
  error: DevUtilError;
  /** Renders `error.detail` as a monospace `<pre>` block (preserving whitespace/newlines) instead
   * of a plain sentence — `RegExpTesterPanel.tsx` needs this, since a regex compile error can come
   * back multi-line with a caret pointer (e.g. `"[unclosed\n        ^"`); `ColorConverterPanel.tsx`
   * doesn't, since its own errors are always one short sentence. Defaults to `false` (plain text). */
  monospaceDetail?: boolean;
}

/**
 * The "Cannot be processed" inline error card an Output panel renders on a failed submit, in place
 * of a toast, when the failure is a common/expected outcome (an invalid regex, a color that
 * doesn't parse) rather than a rare technical error — `RegExpTesterPanel.tsx` and
 * `ColorConverterPanel.tsx` both rendered this exact `Stack`/`ErrorOutlineIcon`/headline/detail
 * shape independently, differing only in whether the detail text needs the monospace/`pre`
 * treatment. Both use plain theme tokens (`error.main`, `alpha(theme.palette.error.main, 0.08)`),
 * not fixed color literals — unlike `DevUtilToolPanel.tsx`'s own error box, which deliberately
 * stays out of this shared component: that panel's whole Output background is independently
 * state-driven (white for empty/error, dark for a real result), so its error box needs fixed
 * literals to match, not the app's own light/dark theme the way every other panel's Output
 * background already does.
 */
export default function InlineErrorBox({ error, monospaceDetail = false }: InlineErrorBoxProps): JSX.Element {
  return (
    <Stack
      direction="row"
      spacing={1.5}
      sx={{
        p: 2,
        borderRadius: 1,
        border: '1px solid',
        borderColor: 'error.main',
        bgcolor: theme => alpha(theme.palette.error.main, 0.08),
      }}
    >
      <ErrorOutlineIcon fontSize="small" color="error" sx={{ mt: '2px' }} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="subtitle2" fontWeight={700} color="error.main">
          {error.headline}
        </Typography>
        <Typography
          component={monospaceDetail ? 'pre' : 'p'}
          variant="body2"
          color="text.primary"
          sx={{
            m: 0,
            whiteSpace: monospaceDetail ? 'pre-wrap' : 'normal',
            wordBreak: 'break-word',
            fontFamily: monospaceDetail ? 'monospace' : undefined,
          }}
        >
          {error.detail}
        </Typography>
      </Box>
    </Stack>
  );
}
