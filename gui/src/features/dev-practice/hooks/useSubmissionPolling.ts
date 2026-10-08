import { useEffect, useRef, useState } from 'react';
import { Submission } from '../types';
import { IN_PROGRESS_STATUSES } from '../constants';

// Judge0 via RapidAPI usually finishes in a few seconds per test case; ~2 minutes covers a slow
// queue. Past that the submission isn't lost — it's still judging server-side, and its final status
// shows up in the submission history once it lands.
const POLL_INTERVAL_MS = 1500;
const MAX_POLLS = 80;

/** PENDING or RUNNING — the judge hasn't reached a verdict yet. */
export function isInProgress(s: Submission | null): boolean {
  return s !== null && IN_PROGRESS_STATUSES.includes(s.status);
}

export interface SubmissionPolling {
  /** The submission being tracked, updated as the judge progresses; null before the first one. */
  submission: Submission | null;
  /** True once polling stopped at `MAX_POLLS` with the submission still in progress. */
  gaveUp: boolean;
  /** Start tracking a submission just returned by a create call (or any other one). */
  track: (submission: Submission) => void;
}

/**
 * Follows one submission until the judge reaches a final status. Judging is asynchronous on the
 * backend (a create call returns PENDING), so this re-fetches every `POLL_INTERVAL_MS` and calls
 * `onFinal` once with the verdict. Shared by the admin reference panel and the learner workspace —
 * the two only differ in which endpoint fetches a submission.
 *
 * Polling is keyed on the tracked submission's id: tracking a new submission re-arms it, and
 * unmounting (or tracking another one) stops the old loop, so a late response never overwrites a
 * newer submission.
 *
 * @param fetchSubmission loads the current state of a submission by id
 * @param onFinal called once per submission when its status becomes final
 */
export function useSubmissionPolling(
  fetchSubmission: (id: number) => Promise<Submission>,
  onFinal?: (submission: Submission) => void,
): SubmissionPolling {
  const [submission, setSubmission] = useState<Submission | null>(null);
  const [gaveUp, setGaveUp] = useState(false);
  // Read through refs so a caller passing inline arrow functions doesn't restart the loop each render.
  const fetchRef = useRef(fetchSubmission);
  fetchRef.current = fetchSubmission;
  const onFinalRef = useRef(onFinal);
  onFinalRef.current = onFinal;

  useEffect(() => {
    if (!submission || !isInProgress(submission)) return;
    let cancelled = false;
    let polls = 0;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      polls += 1;
      try {
        const next = await fetchRef.current(submission.id);
        if (cancelled) return;
        if (isInProgress(next)) {
          setSubmission(next); // e.g. PENDING → RUNNING
          if (polls >= MAX_POLLS) setGaveUp(true);
          else timer = setTimeout(tick, POLL_INTERVAL_MS);
          return;
        }
        setSubmission(next);
        onFinalRef.current?.(next);
      } catch {
        // A failed poll (network blip) just retries; the submission keeps judging server-side.
        if (!cancelled && polls < MAX_POLLS) timer = setTimeout(tick, POLL_INTERVAL_MS);
      }
    };
    timer = setTimeout(tick, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearTimeout(timer); };
    // Re-arm only when a different submission starts, not on every status update of the same one.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submission?.id]);

  const track = (next: Submission) => {
    setGaveUp(false);
    setSubmission(next);
    if (!isInProgress(next)) onFinalRef.current?.(next);
  };

  return { submission, gaveUp, track };
}
