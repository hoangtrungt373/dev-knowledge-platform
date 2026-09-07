import { ReactNode } from 'react';
import { Box, Button, CircularProgress, SxProps, Theme } from '@mui/material';

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
  /** Passed straight through to the underlying `Button`, unconditionally — stays visible even
   * while `saving` (e.g. a "Save" button that keeps its `SaveIcon` next to the spinner), since
   * `startIcon` is MUI's own separate slot from `children`. Omit for a button with no icon. */
  startIcon?: ReactNode;
  sx?: SxProps<Theme>;
}

/** The primary "contained button that swaps its label for a spinner while saving" pattern —
 * byte-for-byte identical across all 6 admin CRUD dialogs before this extraction, plus 4 more
 * near-duplicates with a drifting, undocumented spinner size (16/18/20/24, no rule) — see
 * gui/CLAUDE.md's ecommerce style-audit note. `spinnerSize` now defaults off `size` instead of
 * being picked ad hoc per call site: 24 for a `"large"` CTA, 16 for everything else (MUI's own
 * default `size`, "small", per `theme.ts`).
 *
 * <p>The label is always rendered, just `visibility: hidden` while `saving` — never swapped out
 * for the spinner outright, per a bug report ("the button suddenly glitches/changes its width"):
 * a spinner is narrower than most labels, so replacing the label with it shrank the button for the
 * request's duration and snapped it back on completion. Keeping the (invisible) label in the flow
 * means the button's own width is always driven by `label`, regardless of `saving`.
 *
 * <p>The spinner itself is `position: absolute`, centered over a wrapper `Box` around just the
 * label — **not** over the whole `Button` — via `position: relative` on that same wrapper, not on
 * `Button` itself. A first cut centered it on the `Button`'s own box instead, which looked right
 * with no `startIcon`, but with one (this component's other supported slot, rendered unconditionally
 * alongside the label) the spinner centered over *icon + label combined*, landing visibly off from
 * where the label itself sat — a second bug report ("looks like the button has been re-rendered
 * when clicked") turned out to be this exact misalignment, not a rerender at all.
 *
 * <p>Label ⇄ spinner cross-fade via `opacity` + a short CSS `transition`, not an instant
 * `visibility`/mount toggle — per a **third** report on the exact same underlying issue ("the text
 * in button blink for a moment"): for a request fast enough to resolve in well under a second (the
 * common case for most of this app's own mutations), an un-transitioned `visibility: hidden` ⇄
 * `visible` flip reads as a literal flash, since there's nothing to smooth the two states apart —
 * the spinner isn't even conditionally mounted/unmounted anymore for the same reason (a mount/
 * unmount can't cross-fade; only two already-present elements trading `opacity` can). `pointerEvents:
 * 'none'` on the spinner keeps it from intercepting the button's own click target while faded out
 * (harmless for the click itself, since a click on any child still bubbles to `Button`'s own
 * handler regardless, but avoids it fighting text selection/hover on the label underneath it).
 *
 * <p>**Fourth report, same complaint, root cause finally outside the label/spinner slot
 * entirely**: `disabled={saving || disabled}` makes `Button` apply MUI's own `.Mui-disabled`
 * styling the instant `saving` flips true — a genuinely different color scheme (`action.disabled`
 * text over `action.disabledBackground`, not this button's own contained-primary look) — so for a
 * fast request the *whole button* was flashing to a muted grey and back, independent of (and more
 * visible than) the label/spinner cross-fade above, which the first three fixes never touched
 * since they only ever looked at the label/spinner slot. `disabled` still has to stay wired to
 * `saving` (a real, unrelated invalid-input `disabled` needs to keep looking disabled, and a
 * genuinely slow request still needs the click blocked) — so instead of dropping that, `sx`
 * conditionally overrides `&.Mui-disabled`'s own color/background back to the plain
 * `primary.main`/`primary.contrastText` contained look, but **only while `saving`**, not for a
 * real `disabled` prop — an invalid-input button should still look visibly disabled; only the
 * saving-induced flash needed suppressing. */
export default function SubmitButton({
  saving,
  label,
  onClick,
  type = 'button',
  disabled = false,
  fullWidth = false,
  size,
  spinnerSize,
  startIcon,
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
      startIcon={startIcon}
      sx={{
        ...(saving && {
          '&.Mui-disabled': {
            backgroundColor: 'primary.main',
            color: 'primary.contrastText',
          },
        }),
        ...sx,
      }}
    >
      <Box component="span" sx={{ position: 'relative', display: 'inline-flex' }}>
        <Box component="span" sx={{ opacity: saving ? 0 : 1, transition: 'opacity 0.15s ease' }}>
          {label}
        </Box>
        <CircularProgress
          size={resolvedSpinnerSize}
          color="inherit"
          sx={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            marginTop: `-${resolvedSpinnerSize / 2}px`,
            marginLeft: `-${resolvedSpinnerSize / 2}px`,
            opacity: saving ? 1 : 0,
            transition: 'opacity 0.15s ease',
            pointerEvents: 'none',
          }}
        />
      </Box>
    </Button>
  );
}
