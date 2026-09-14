import { useCallback, useState } from 'react';

import { TabKey } from '../config/operations';

// A standing, per-browser preference — same "persist across reloads, client-side only" shape
// SIDEBAR_COLLAPSE_STORAGE_KEY (DevUtilsPage.tsx) already establishes. There is no backend concept
// of a "favorite" operation at all, and none is needed for this: it's purely a personal shortcut
// into an otherwise-flat tool list, so localStorage alone is the right (and simplest) store.
const FAVORITES_STORAGE_KEY = 'devUtilsFavoriteOperations';

function readStoredFavorites(): TabKey[] {
  try {
    const raw = localStorage.getItem(FAVORITES_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((key): key is TabKey => typeof key === 'string') : [];
  } catch {
    // A corrupted/foreign value under this key shouldn't break the page — just start empty.
    return [];
  }
}

/** Manages the client-side-only set of "favorite" dev-utils operations, per request — pinned to
 * their own sidebar section and toggleable both from the sidebar and the active-operation header.
 * Persisted to `localStorage` only; nothing is ever sent to the backend. */
export function useFavoriteOperations() {
  const [favorites, setFavorites] = useState<Set<TabKey>>(() => new Set(readStoredFavorites()));

  const toggleFavorite = useCallback((key: TabKey) => {
    setFavorites(prev => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      try {
        localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify([...next]));
      } catch {
        // Best-effort only (e.g. a private window with storage disabled) — the in-memory toggle
        // still works for the rest of this session even if it can't be persisted.
      }
      return next;
    });
  }, []);

  const isFavorite = useCallback((key: TabKey) => favorites.has(key), [favorites]);

  return { favorites, isFavorite, toggleFavorite };
}
