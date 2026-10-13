import { ProblemProgressStatus } from '../types';

type StatusOf = (problemId: number) => ProblemProgressStatus | undefined;
type SolvedAtOf = (problemId: number) => string | undefined;

/** How many of `problems` the learner has solved — the "x" in a plan's or section's "x / y solved". */
export function countSolved(problems: { id: number }[], statusOf: StatusOf): number {
  return problems.filter(p => statusOf(p.id) === 'SOLVED').length;
}

/**
 * The first problem, in plan order, the learner hasn't solved — attempted counts as unsolved — or
 * `undefined` once every one is solved. What a plan's "Start"/"Continue" button opens.
 */
export function firstUnsolved<T extends { id: number }>(problems: T[], statusOf: StatusOf): T | undefined {
  return problems.find(p => statusOf(p.id) !== 'SOLVED');
}

/**
 * The `limit` most recently solved of `problems`, newest first, each with when it was first solved —
 * a plan's "Recently solved" strip. Problems without a solved time are left out.
 */
export function recentlySolved<T extends { id: number }>(
  problems: T[],
  solvedAtOf: SolvedAtOf,
  limit: number,
): { problem: T; solvedAt: string }[] {
  return problems
    .flatMap(problem => {
      const solvedAt = solvedAtOf(problem.id);
      return solvedAt ? [{ problem, solvedAt }] : [];
    })
    .sort((a, b) => new Date(b.solvedAt).getTime() - new Date(a.solvedAt).getTime())
    .slice(0, limit);
}
