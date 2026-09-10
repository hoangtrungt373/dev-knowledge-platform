import { useCallback, useState } from 'react';

/**
 * "Maximize this panel" state — extracted from `DevUtilToolPanel.tsx`'s own original inline
 * `maximizedPanel` state once `RegExpTesterPanel.tsx`/`TextDiffPanel.tsx` needed the identical
 * toggle for their own Input/Output-shaped pair. Generic over the pair of keys a given panel uses
 * (`'input' | 'output'` for the shared panel, `'left' | 'right'` for a panel with a differently
 * named pair) so each caller keeps its own meaningful key names rather than being forced onto one
 * fixed pair.
 *
 * <p>Deliberately plain component state, not persisted to `localStorage` the way
 * `useResizableSplit`'s own ratio is — maximizing reads as a momentary focus mode (closer to a
 * video call's "pin this speaker" than a standing layout choice), so a fresh mount (a tool switch
 * remounts every one of these panels via `key={...}` in `DevUtilsPage.tsx`) always starts
 * unmaximized rather than following the admin from tool to tool.
 */
export function usePanelMaximize<TKey extends string>() {
  const [maximizedPanel, setMaximizedPanel] = useState<TKey | null>(null);

  const toggle = useCallback((key: TKey) => {
    setMaximizedPanel(prev => (prev === key ? null : key));
  }, []);

  return { maximizedPanel, toggle };
}
