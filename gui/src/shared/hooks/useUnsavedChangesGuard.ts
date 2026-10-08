import { useEffect, useRef } from 'react';
import { Blocker, useBlocker } from 'react-router-dom';

export interface UnsavedChangesGuard {
  /** Feed to `UnsavedChangesDialog` — `state === 'blocked'` while an in-app navigation waits. */
  blocker: Blocker;
  /**
   * Lets the next navigation through unconditionally — call right before a navigation that is
   * itself the result of saving (e.g. create → edit page), which would otherwise be blocked because
   * the form only becomes "clean" a render later.
   */
  allowNextNavigation: () => void;
}

/**
 * Warns before leaving a page with unsaved changes, on both kinds of exit:
 * - **in-app navigation** (links, back/forward, `navigate()`) — blocked via React Router's
 *   `useBlocker` until the caller's dialog proceeds or resets it. Only a *different pathname* is
 *   blocked: a search/hash change (e.g. a form's `?tab=`) stays on the same page and keeps its state.
 * - **reload / closing the tab / typing a URL** — via the browser's own `beforeunload` prompt; its
 *   text is fixed by the browser, a page can only ask for it to be shown.
 *
 * Needs a data router (`createBrowserRouter`, see `main.tsx`) — `useBlocker` throws under a plain
 * `<BrowserRouter>`.
 *
 * @param isDirty whether the page currently holds changes that leaving would lose
 */
export function useUnsavedChangesGuard(isDirty: boolean): UnsavedChangesGuard {
  const bypassRef = useRef(false);

  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (bypassRef.current) {
      bypassRef.current = false;
      return false;
    }
    return isDirty && currentLocation.pathname !== nextLocation.pathname;
  });

  useEffect(() => {
    if (!isDirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // Legacy browsers only show the prompt when returnValue is set; modern ones ignore its text.
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [isDirty]);

  return { blocker, allowNextNavigation: () => { bypassRef.current = true; } };
}
