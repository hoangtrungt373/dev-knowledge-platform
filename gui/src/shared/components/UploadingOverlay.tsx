import { ReactNode } from 'react';
import { Box } from '@mui/material';
import { alpha } from '@mui/material/styles';

interface UploadingOverlayProps {
  children: ReactNode;
  /** Matches the shape of whatever it's covering — `1` (theme spacing, a rectangular thumbnail) or
   * `'50%'` (a circular avatar). Default `1`. */
  borderRadius?: number | string;
  /** When true, the overlay starts hidden and fades in on hover — for a "click to change" hint
   * rather than an in-progress state. The element this wraps must have `position: 'relative'`.
   * Default `false` (always visible). */
  revealOnHover?: boolean;
}

/**
 * The dark, centered-content overlay for an image thumbnail/avatar mid-upload (or, with
 * `revealOnHover`, a hover-triggered "click to change" hint) — `position: 'absolute', inset: 0`
 * over a `position: 'relative'` parent, tinted `alpha(theme.palette.common.black, 0.45)`. Extracted
 * after the same hardcoded `bgcolor: 'rgba(0,0,0,0.45)'` literal turned up independently in
 * `@ecommerce/CouponFormDialog.tsx` and `@auth/ProfilePage.tsx` — see `gui/CLAUDE.md`'s style-audit
 * note. Content (a `CircularProgress` while uploading, a `PhotoCameraIcon` for the hover hint) is
 * passed as `children` rather than baked in, since the two existing call sites need different ones.
 */
export default function UploadingOverlay({ children, borderRadius = 1, revealOnHover = false }: UploadingOverlayProps): JSX.Element {
  return (
    <Box
      sx={{
        position: 'absolute',
        inset: 0,
        borderRadius,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: (theme) => alpha(theme.palette.common.black, 0.45),
        ...(revealOnHover && {
          opacity: 0,
          transition: 'opacity 0.2s',
          '&:hover': { opacity: 1 },
        }),
      }}
    >
      {children}
    </Box>
  );
}
