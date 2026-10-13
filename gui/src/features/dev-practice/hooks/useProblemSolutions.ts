import { useCallback, useEffect, useState } from 'react';
import { ProblemSolution } from '../types';
import { practiceApi } from '../api/practiceApi';

export interface ProblemSolutionsState {
  /** null until the first successful load. */
  solutions: ProblemSolution[] | null;
  loading: boolean;
  /** True when the last load failed; `retry` tries again. */
  failed: boolean;
  retry: () => void;
}

/**
 * A published problem's editorial (`GET /api/v1/public/problems/{slug}/solutions`), loaded lazily: the
 * first time `enabled` turns true — i.e. the learner opens the Solution tab — and kept afterwards, so
 * switching tabs never refetches. Lives at the workspace level because the left panel renders its
 * tabs conditionally. A failure is shown inline by the caller (no toast) with a retry.
 */
export function useProblemSolutions(slug: string, enabled: boolean): ProblemSolutionsState {
  const [solutions, setSolutions] = useState<ProblemSolution[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  // Bumped by retry() to re-run the effect after a failure.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled || solutions !== null) return;
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    practiceApi.getSolutions(slug)
      .then(data => { if (!cancelled) setSolutions(data); })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // solutions is deliberately left out: once loaded, nothing should trigger another fetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug, enabled, attempt]);

  const retry = useCallback(() => setAttempt(a => a + 1), []);

  return { solutions, loading, failed, retry };
}
