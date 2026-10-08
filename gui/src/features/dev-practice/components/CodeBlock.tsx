import { Box, Typography } from '@mui/material';

interface Props {
  children: string;
  /** Optional caption above the block ("Input", "Expected", …). */
  label?: string;
  /**
   * `'panel'` — tinted background with padding, for values shown on their own (test-case inputs,
   * outputs). `'plain'` — just the monospace text, for output inside an `Alert` that already has a
   * background (compiler errors, stack traces).
   */
  variant?: 'panel' | 'plain';
}

/**
 * Preformatted monospace text that wraps instead of scrolling sideways — every place this feature
 * shows code-ish values (arguments, outputs, judge diagnostics) uses it, so they all look alike.
 */
export default function CodeBlock({ children, label, variant = 'panel' }: Props): JSX.Element {
  const panel = variant === 'panel';
  return (
    <Box>
      {label && (
        <Typography variant="caption" color="text.secondary" fontWeight={700} component="div">{label}</Typography>
      )}
      <Box
        component="pre"
        sx={{
          m: 0,
          mt: label ? 0.5 : panel ? 0 : 1,
          fontFamily: 'monospace',
          fontSize: panel ? '0.8rem' : '0.75rem',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          ...(panel && { p: 1, borderRadius: 1, bgcolor: 'action.hover' }),
        }}
      >
        {children}
      </Box>
    </Box>
  );
}
