import { ReactNode } from 'react';
import { Button, CircularProgress, SxProps, Theme } from '@mui/material';

interface SubmitButtonProps {
  saving: boolean;
  label: ReactNode;
  onClick?: () => void;
  type?: 'button' | 'submit';
  disabled?: boolean;
  fullWidth?: boolean;
  size?: 'small' | 'medium' | 'large';
  /** Overrides the size-driven default below — only reach for this if a specific spot genuinely
   * needs a different spinner size, not as a matter of taste. */
  spinnerSize?: number;
  sx?: SxProps<Theme>;
}

/** The primary "contained button that swaps its label for a spinner while saving" pattern —
 * byte-for-byte identical across all 6 admin CRUD dialogs before this extraction, plus 4 more
 * near-duplicates with a drifting, undocumented spinner size (16/18/20/24, no rule) — see
 * gui/CLAUDE.md's ecommerce style-audit note. `spinnerSize` now defaults off `size` instead of
 * being picked ad hoc per call site: 24 for a `"large"` CTA, 16 for everything else (MUI's own
 * default `size`, "small", per `theme.ts`). */
export default function SubmitButton({
  saving,
  label,
  onClick,
  type = 'button',
  disabled = false,
  fullWidth = false,
  size,
  spinnerSize,
  sx,
}: SubmitButtonProps): JSX.Element {
  const resolvedSpinnerSize = spinnerSize ?? (size === 'large' ? 24 : 16);
  return (
    <Button
      type={type}
      variant="contained"
      onClick={onClick}
      disabled={saving || disabled}
      fullWidth={fullWidth}
      size={size}
      sx={sx}
    >
      {saving ? <CircularProgress size={resolvedSpinnerSize} color="inherit" /> : label}
    </Button>
  );
}
