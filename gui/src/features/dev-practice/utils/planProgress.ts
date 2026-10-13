import { ProblemProgressStatus } from '../types';

type StatusOf = (problemId: number) => ProblemProgressStatus | undefined;

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
