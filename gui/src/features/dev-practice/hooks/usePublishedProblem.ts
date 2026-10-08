import { useEffect, useState } from 'react';
import { Problem } from '../types';
import { practiceApi } from '../api/practiceApi';

export interface PublishedProblem {
  problem: Problem | null;
  loading: boolean;
  /** No published problem has this slug (missing and unpublished look the same, on purpose). */
  notFound: boolean;
}

/**
 * Loads one published problem by slug for the learner workspace. No error toast: a missing problem
 * gets the page's own "not found" state instead. A response that arrives after the slug changed (or
 * the page unmounted) is dropped.
 */
export function usePublishedProblem(slug: string): PublishedProblem {
  const [problem, setProblem] = useState<Problem | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    practiceApi.getProblem(slug)
      .then(p => { if (!cancelled) setProblem(p); })
      .catch(() => { if (!cancelled) setNotFound(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  return { problem, loading, notFound };
}
