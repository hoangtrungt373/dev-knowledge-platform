import { KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';
import { Box } from '@mui/material';

interface PanelResizeHandleProps {
  ariaLabel: string;
  splitPercent: number;
  minPercent: number;
  maxPercent: number;
  resizing: boolean;
  /** Hides the handle entirely — e.g. while either side is maximized (nothing to drag when one
   * side is `display: 'none'`). Independent of the `orientation: 'horizontal'` breakpoint hiding
   * below, which applies regardless of this flag. */
  hidden: boolean;
  /** `'horizontal'` (default) draws a left/right divider (`cursor: 'col-resize'`, a vertical
   * highlight line) — every original consumer's shape. `'vertical'` draws a top/bottom divider
   * (`cursor: 'row-resize'`, a horizontal highlight line) instead, for a caller resizing two
   * *stacked* siblings (`Base64ImagePanel.tsx`'s own Upload/Image Data URL split) rather than two
   * side-by-side ones. Must match whatever orientation the paired `useResizableSplit` hook was
   * given — this component has no way to detect a mismatch itself. */
  orientation?: 'horizontal' | 'vertical';
  onPointerDown: (e: ReactPointerEvent<HTMLDivElement>) => void;
  onPointerMove: (e: ReactPointerEvent<HTMLDivElement>) => void;
  onPointerUp: (e: ReactPointerEvent<HTMLDivElement>) => void;
  onDoubleClick: () => void;
  onKeyDown: (e: ReactKeyboardEvent<HTMLDivElement>) => void;
}

/**
 * The resizable divider `DevUtilToolPanel.tsx` originally drew inline as part of its own JSX —
 * extracted once `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` needed the identical overlay for
 * their own Input/Output-shaped row (see `hooks/useResizableSplit.ts`'s own doc comment for the
 * matching split-state extraction this pairs with). Purely presentational, no state of its own.
 *
 * <p>Overlays the shared border between two flex siblings whose own `flex-basis` percentages sum
 * to 100% with no `gap` between them — for a horizontal split, `left: ${splitPercent}%` against
 * the parent row's own `position: 'relative'` lands exactly on that border only because of that
 * "sums to 100%, no gap" precondition (a vertical split is the same, just `top` instead of
 * `left`); the caller's row must be built that way. Renders no visible line at rest — the two
 * sides' own adjacent borders already read as a single seam where they touch — only a fade-in
 * `opacity` highlight on hover/focus/drag. The hit target (16px along the drag axis) is
 * deliberately much wider than the *visible* line it reveals: a bare 1-2px seam is a poor target
 * to land a mouse on precisely (the same reasoning `react-resizable-panels`' own
 * `resizeTargetMinimumSize` docs and Apple's HIG make for a real handle).
 *
 * <p>The `{ xs: 'none', md: 'block' }` breakpoint hiding only applies to a **horizontal** split —
 * a narrow viewport wraps two side-by-side cards onto separate full-width lines (via
 * `flexWrap`/`minWidth`), where a left/right drag handle wouldn't mean anything. A **vertical**
 * split's two siblings are already stacked at every viewport width (nothing to wrap), so that
 * handle stays visible regardless of width — gated only by the `hidden` prop.
 */
export default function PanelResizeHandle({
  ariaLabel,
  splitPercent,
  minPercent,
  maxPercent,
  resizing,
  hidden,
  orientation = 'horizontal',
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onDoubleClick,
  onKeyDown,
}: PanelResizeHandleProps): JSX.Element {
  const isVertical = orientation === 'vertical';
  return (
    <Box
      role="separator"
      aria-orientation={isVertical ? 'horizontal' : 'vertical'}
      aria-label={ariaLabel}
      aria-valuenow={Math.round(splitPercent)}
      aria-valuemin={minPercent}
      aria-valuemax={maxPercent}
      tabIndex={0}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onDoubleClick={onDoubleClick}
      onKeyDown={onKeyDown}
      sx={{
        display: hidden ? 'none' : isVertical ? 'block' : { xs: 'none', md: 'block' },
        position: 'absolute',
        ...(isVertical
          ? { left: 0, right: 0, top: `${splitPercent}%`, transform: 'translateY(-50%)', height: 16 }
          : { top: 0, bottom: 0, left: `${splitPercent}%`, transform: 'translateX(-50%)', width: 16 }),
        zIndex: 1,
        cursor: isVertical ? 'row-resize' : 'col-resize',
        outline: 'none',
        '&::after': isVertical
          ? {
              content: '""',
              position: 'absolute',
              left: 0,
              right: 0,
              top: '50%',
              height: 2,
              transform: 'translateY(-50%)',
              bgcolor: 'primary.main',
              opacity: 0,
              transition: 'opacity 0.1s',
            }
          : {
              content: '""',
              position: 'absolute',
              top: 0,
              bottom: 0,
              left: '50%',
              width: 2,
              transform: 'translateX(-50%)',
              bgcolor: 'primary.main',
              opacity: 0,
              transition: 'opacity 0.1s',
            },
        '&:hover::after, &:focus-visible::after': { opacity: 1 },
        ...(resizing && {
          '&::after': { opacity: 1 },
        }),
      }}
    />
  );
}
