import { useCallback, useEffect, useState } from 'react';
import { ProblemProgressStatus } from '../types';
import { practiceApi } from '../api/practiceApi';
import { authService } from '@auth/services/authService';

export interface ProblemProgressMap {
  /** The learner's status on a problem, or undefined if they never submitted to it (or are logged out). */
  statusOf: (problemId: number) => ProblemProgressStatus | undefined;
  /** When the learner first solved a problem (ISO instant), or undefined if they haven't. */
  solvedAtOf: (problemId: number) => string | undefined;
  solvedCount: number;
  /**
   * Whether statuses are final for this page: true once the fetch settles (either way), and right
   * away when logged out. Lets a caller wait instead of briefly treating every problem as untouched.
   */
  loaded: boolean;
  /** Records a verdict that just landed, so markers update without refetching. */
  markJudged: (problemId: number, accepted: boolean) => void;
}

interface Entry {
  status: ProblemProgressStatus;
  solvedAt?: string;
}

/**
 * The logged-in learner's solved/attempted status per problem, and when each was first solved, loaded
 * once (`GET /api/v1/submissions/progress` — one row per problem touched, so small and unpaged).
 * Logged out, nothing is fetched and every problem simply has no status. A failed fetch is silent:
 * markers are a convenience, and the pages work fully without them.
 */
export function useProblemProgress(): ProblemProgressMap {
  const [entries, setEntries] = useState<Map<number, Entry>>(new Map());
  const [loaded, setLoaded] = useState(() => !authService.isAuthenticated());

  useEffect(() => {
    if (!authService.isAuthenticated()) return;
    let cancelled = false;
    practiceApi.getProgress()
      .then(rows => {
        if (cancelled) return;
        setEntries(new Map(rows.map(r => [r.problemId, { status: r.status, solvedAt: r.solvedAt ?? undefined }])));
      })
      .catch(() => { /* no markers this time */ })
      .finally(() => { if (!cancelled) setLoaded(true); });
    return () => { cancelled = true; };
  }, []);

  const markJudged = useCallback((problemId: number, accepted: boolean) => {
    setEntries(prev => {
      // Solved is sticky: a later failed attempt never turns a solved problem back into attempted, and
      // the first-solved time never moves.
      if (prev.get(problemId)?.status === 'SOLVED') return prev;
      const next = new Map(prev);
      // A fresh accept is stamped client-side; the server's own time replaces it on the next load.
      next.set(problemId, accepted ? { status: 'SOLVED', solvedAt: new Date().toISOString() } : { status: 'ATTEMPTED' });
      return next;
    });
  }, []);

  let solvedCount = 0;
  entries.forEach(e => { if (e.status === 'SOLVED') solvedCount += 1; });

  return {
    statusOf: id => entries.get(id)?.status,
    solvedAtOf: id => entries.get(id)?.solvedAt,
    solvedCount,
    loaded,
    markJudged,
  };
}
