import { useCallback, useEffect, useState } from 'react';
import { ProblemSolution, ProgrammingLanguage } from '../types';
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
 * A published problem's editorial (`GET /api/v1/public/problems/{slug}/solutions?language=`), loaded
 * lazily: the first time `enabled` turns true — i.e. the learner opens the Solution tab — and again
 * only when `language` changes (the server re-renders each explanation's code in that language where
 * the approach has it). While a language switch loads, the previous editorial stays on screen, so the
 * code swaps in place instead of flashing to a spinner. Lives at the workspace level because the left
 * panel renders its tabs conditionally. A failure is shown inline by the caller (no toast) with a retry.
 */
export function useProblemSolutions(
  slug: string,
  enabled: boolean,
  language: ProgrammingLanguage,
): ProblemSolutionsState {
  const [solutions, setSolutions] = useState<ProblemSolution[] | null>(null);
  // Which language the current `solutions` were rendered in; undefined until the first load.
  const [loadedLanguage, setLoadedLanguage] = useState<ProgrammingLanguage | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  // Bumped by retry() to re-run the effect after a failure.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled || loadedLanguage === language) return;
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    practiceApi.getSolutions(slug, language)
      .then(data => {
        if (cancelled) return;
        setSolutions(data);
        setLoadedLanguage(language);
      })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // loadedLanguage is deliberately left out: it only changes as the result of this very effect.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug, enabled, language, attempt]);

  const retry = useCallback(() => setAttempt(a => a + 1), []);

  return { solutions, loading, failed, retry };
}
