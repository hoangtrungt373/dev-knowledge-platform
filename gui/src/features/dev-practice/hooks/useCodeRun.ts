import { useEffect, useRef, useState } from 'react';
import { Problem, ProgrammingLanguage, RunResult } from '../types';
import { practiceApi } from '../api/practiceApi';
import { casesFromSamples, toInputJson } from '../utils/runCases';

/** What the last Run produced: a result, or a message (bad input, judge unavailable). */
export type RunOutcome =
  | { kind: 'result'; result: RunResult; runId: number }
  | { kind: 'error'; message: string };

export interface CodeRun {
  /** The console's editable cases — one JSON value per parameter per case. */
  cases: string[][];
  setCases: (cases: string[][]) => void;
  /** Puts the problem's sample cases back. */
  resetCases: () => void;
  running: boolean;
  outcome: RunOutcome | null;
  /** Runs the given code on the current cases. Never throws — a failure becomes an `error` outcome. */
  run: (language: ProgrammingLanguage, code: string) => Promise<void>;
}

/**
 * The learner workspace's Run state: the editable cases (pre-filled from the samples once the
 * problem has loaded), whether a run is in flight, and its last outcome. Run is synchronous on the
 * backend, so there's nothing to poll — unlike Submit (`useSubmissionPolling`).
 *
 * Every case is sent as a custom input: the backend still checks one that equals a sample against
 * that sample's answer, so untouched pre-filled cases come back passed/failed while edited ones just
 * show their output.
 */
export function useCodeRun(problem: Problem | null): CodeRun {
  const [cases, setCases] = useState<string[][]>([]);
  const [running, setRunning] = useState(false);
  const [outcome, setOutcome] = useState<RunOutcome | null>(null);
  // Keys each result, so the result view starts again at Case 1 for a new run.
  const runCounter = useRef(0);

  useEffect(() => {
    setCases(problem ? casesFromSamples(problem) : []);
  }, [problem]);

  const run = async (language: ProgrammingLanguage, code: string) => {
    if (!problem) return;
    setRunning(true);
    try {
      const result = await practiceApi.run(problem.id, language, code, cases.map(toInputJson));
      runCounter.current += 1;
      setOutcome({ kind: 'result', result, runId: runCounter.current });
    } catch (e) {
      // Not a toast: a bad custom input's message ("Custom input #2 isn't valid: …") belongs next to it.
      setOutcome({ kind: 'error', message: e instanceof Error ? e.message : 'The run failed' });
    } finally {
      setRunning(false);
    }
  };

  return {
    cases,
    setCases,
    resetCases: () => setCases(problem ? casesFromSamples(problem) : []),
    running,
    outcome,
    run,
  };
}
