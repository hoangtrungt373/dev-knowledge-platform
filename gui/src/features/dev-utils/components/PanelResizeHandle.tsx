import { KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';
import { Box } from '@mui/material';

interface PanelResizeHandleProps {
  ariaLabel: string;
  splitPercent: number;
  minPercent: number;
  maxPercent: number;
  resizing: boolean;
  /** Hides the handle entirely — e.g. while either side is maximized (nothing to drag when one
   * side is `display: 'none'`). Independent of the `{ xs: 'none', md: 'block' }` breakpoint this
   * component always applies underneath — a narrow viewport wraps the two sides onto separate
   * full-width lines, where a horizontal drag handle wouldn't mean anything either. */
  hidden: boolean;
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
 * to 100% with no `gap` between them — `left: ${splitPercent}%` against the parent row's own
 * `position: 'relative'` lands exactly on that border only because of that "sums to 100%, no gap"
 * precondition; the caller's row must be built that way. Renders no visible line at rest —
 * the two sides' own adjacent borders already read as a single seam where they touch — only a
 * fade-in `opacity` highlight on hover/focus/drag. The hit target (`width: 16`) is deliberately
 * much wider than the *visible* line it reveals: a bare 1-2px seam is a poor target to land a
 * mouse on precisely (the same reasoning `react-resizable-panels`' own `resizeTargetMinimumSize`
 * docs and Apple's HIG make for a real handle).
 */
export default function PanelResizeHandle({
  ariaLabel,
  splitPercent,
  minPercent,
  maxPercent,
  resizing,
  hidden,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onDoubleClick,
  onKeyDown,
}: PanelResizeHandleProps): JSX.Element {
  return (
    <Box
      role="separator"
      aria-orientation="vertical"
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
        display: hidden ? 'none' : { xs: 'none', md: 'block' },
        position: 'absolute',
        top: 0,
        bottom: 0,
        left: `${splitPercent}%`,
        transform: 'translateX(-50%)',
        width: 16,
        zIndex: 1,
        cursor: 'col-resize',
        outline: 'none',
        '&::after': {
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
