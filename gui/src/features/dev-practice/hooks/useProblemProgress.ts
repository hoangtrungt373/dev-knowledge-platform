import { useCallback, useEffect, useState } from 'react';
import { ProblemProgressStatus } from '../types';
import { practiceApi } from '../api/practiceApi';
import { authService } from '@auth/services/authService';

export interface ProblemProgressMap {
  /** The learner's status on a problem, or undefined if they never submitted to it (or are logged out). */
  statusOf: (problemId: number) => ProblemProgressStatus | undefined;
  solvedCount: number;
  /** Records a verdict that just landed, so markers update without refetching. */
  markJudged: (problemId: number, accepted: boolean) => void;
}

/**
 * The logged-in learner's solved/attempted status per problem, loaded once
 * (`GET /api/v1/submissions/progress` — one row per problem touched, so small and unpaged). Logged
 * out, nothing is fetched and every problem simply has no status. A failed fetch is silent: markers
 * are a convenience, and the pages work fully without them.
 */
export function useProblemProgress(): ProblemProgressMap {
  const [statuses, setStatuses] = useState<Map<number, ProblemProgressStatus>>(new Map());

  useEffect(() => {
    if (!authService.isAuthenticated()) return;
    let cancelled = false;
    practiceApi.getProgress()
      .then(rows => { if (!cancelled) setStatuses(new Map(rows.map(r => [r.problemId, r.status]))); })
      .catch(() => { /* no markers this time */ });
    return () => { cancelled = true; };
  }, []);

  const markJudged = useCallback((problemId: number, accepted: boolean) => {
    setStatuses(prev => {
      // Solved is sticky: a later failed attempt never turns a solved problem back into attempted.
      if (prev.get(problemId) === 'SOLVED') return prev;
      const next = new Map(prev);
      next.set(problemId, accepted ? 'SOLVED' : 'ATTEMPTED');
      return next;
    });
  }, []);

  let solvedCount = 0;
  statuses.forEach(s => { if (s === 'SOLVED') solvedCount += 1; });

  return { statusOf: id => statuses.get(id), solvedCount, markJudged };
}
