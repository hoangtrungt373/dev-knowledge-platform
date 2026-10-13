import { useCallback, useState } from 'react';
import { PRACTICE_HIDE_SOLVED_STORAGE_KEY } from '@shared/constants/storage';

function readStored(): boolean {
  try {
    return localStorage.getItem(PRACTICE_HIDE_SOLVED_STORAGE_KEY) === 'true';
  } catch {
    return false; // storage blocked (private window, disabled site data) — just start unhidden
  }
}

/**
 * The study-plan page's "Hide solved" preference, remembered across visits and plans in
 * `localStorage` — a per-viewer convenience, so a blocked or cleared storage just means it starts off.
 *
 * @returns the current value and a setter that also persists it
 */
export function useHideSolved(): [boolean, (hide: boolean) => void] {
  const [hideSolved, setHideSolvedState] = useState(readStored);

  const setHideSolved = useCallback((hide: boolean) => {
    setHideSolvedState(hide);
    try {
      localStorage.setItem(PRACTICE_HIDE_SOLVED_STORAGE_KEY, String(hide));
    } catch {
      // not persisted this time; the toggle still works for this visit
    }
  }, []);

  return [hideSolved, setHideSolved];
}
