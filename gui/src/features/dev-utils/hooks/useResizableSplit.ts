import {
  KeyboardEvent as ReactKeyboardEvent,
  MutableRefObject,
  PointerEvent as ReactPointerEvent,
  useCallback,
  useRef,
  useState,
} from 'react';

export interface UseResizableSplitOptions {
  /** `localStorage` key this split ratio is persisted under — the only thing that has to differ
   * between callers; each panel persists its own split independently. */
  storageKey: string;
  defaultPercent?: number;
  minPercent?: number;
  maxPercent?: number;
  keyboardStep?: number;
}

export interface UseResizableSplit {
  /** Attach to the row containing both resized siblings (`position: 'relative'`) — the drag math
   * below is only meaningful relative to this element's own bounding box. */
  rowRef: MutableRefObject<HTMLDivElement | null>;
  splitPercent: number;
  resizing: boolean;
  minPercent: number;
  maxPercent: number;
  handlePointerDown: (e: ReactPointerEvent<HTMLDivElement>) => void;
  handlePointerMove: (e: ReactPointerEvent<HTMLDivElement>) => void;
  handlePointerUp: (e: ReactPointerEvent<HTMLDivElement>) => void;
  handleDoubleClick: () => void;
  handleKeyDown: (e: ReactKeyboardEvent<HTMLDivElement>) => void;
}

/**
 * A user-draggable Input/Output-shaped split, persisted to `localStorage` as a standing per-viewer
 * preference. Extracted from `DevUtilToolPanel.tsx`'s own original inline implementation once
 * `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` needed the identical mechanism for their own
 * Input/Output-shaped rows — see that component's own historical `SPLIT_STORAGE_KEY` comment for
 * why this is hand-rolled with plain Pointer Events rather than built on `react-resizable-panels`
 * (already installed, used by `@tasks`): that library's own `Group` container assumes it fills a
 * bounded, already-known-height parent, which is fundamentally incompatible with an Output-shaped
 * side that can grow past the viewport and let the *page* scroll instead (see
 * `config/panelSizing.ts`).
 *
 * <p>`setPointerCapture` (in `handlePointerDown`) routes every subsequent pointer event to the
 * handle element regardless of where the cursor actually moves (even outside the handle's own
 * bounds) until pointerup/cancel — this is what lets `handlePointerMove`/`handlePointerUp` stay
 * plain React props on the handle itself, with no window-level listener to attach/clean up by
 * hand. The ratio is persisted only on release/double-click/keypress (never on every
 * `pointermove` tick), mirroring `react-resizable-panels`' own `onLayoutChanged` guidance — "not
 * called until the pointer has been released" is the recommended point to save to storage.
 */
export function useResizableSplit({
  storageKey,
  defaultPercent = 50,
  minPercent = 25,
  maxPercent = 75,
  keyboardStep = 5,
}: UseResizableSplitOptions): UseResizableSplit {
  const rowRef = useRef<HTMLDivElement | null>(null);
  const [resizing, setResizing] = useState(false);

  const clamp = useCallback((value: number) => Math.min(maxPercent, Math.max(minPercent, value)), [minPercent, maxPercent]);

  // Wrapped in try/catch — a private window or blocked storage should degrade to the default
  // split, never throw.
  const readStored = useCallback((): number => {
    try {
      const stored = window.localStorage.getItem(storageKey);
      const parsed = stored === null ? NaN : Number(stored);
      return Number.isFinite(parsed) ? clamp(parsed) : defaultPercent;
    } catch {
      return defaultPercent;
    }
  }, [storageKey, defaultPercent, clamp]);

  const [splitPercent, setSplitPercent] = useState<number>(readStored);

  const persist = useCallback(
    (value: number) => {
      try {
        window.localStorage.setItem(storageKey, String(value));
      } catch {
        // Best-effort only — a private window or blocked storage just means the split isn't
        // remembered next time, not a real failure worth surfacing.
      }
    },
    [storageKey]
  );

  const handlePointerDown = useCallback((e: ReactPointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    setResizing(true);
  }, []);

  const handlePointerMove = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      if (!resizing || !rowRef.current) {
        return;
      }
      const rect = rowRef.current.getBoundingClientRect();
      const rawPercent = ((e.clientX - rect.left) / rect.width) * 100;
      setSplitPercent(clamp(rawPercent));
    },
    [resizing, clamp]
  );

  const handlePointerUp = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      e.currentTarget.releasePointerCapture(e.pointerId);
      setResizing(false);
      setSplitPercent(current => {
        persist(current);
        return current;
      });
    },
    [persist]
  );

  const handleDoubleClick = useCallback(() => {
    setSplitPercent(defaultPercent);
    persist(defaultPercent);
  }, [defaultPercent, persist]);

  const handleKeyDown = useCallback(
    (e: ReactKeyboardEvent<HTMLDivElement>) => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') {
        return;
      }
      e.preventDefault();
      const delta = e.key === 'ArrowLeft' ? -keyboardStep : keyboardStep;
      setSplitPercent(prev => {
        const next = clamp(prev + delta);
        persist(next);
        return next;
      });
    },
    [keyboardStep, clamp, persist]
  );

  return {
    rowRef,
    splitPercent,
    resizing,
    minPercent,
    maxPercent,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    handleDoubleClick,
    handleKeyDown,
  };
}
