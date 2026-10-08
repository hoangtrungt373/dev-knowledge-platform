import { MethodParameter, Problem } from '../types';

/** Parameters in signature order (the order every JSON argument array follows). */
export function orderedParameters(parameters: MethodParameter[]): MethodParameter[] {
  return [...parameters].sort((a, b) => a.position - b.position);
}

/**
 * Turns a JSON argument array into `name = value` lines, in parameter order — the way a problem
 * statement writes an example (`nums = [1,2,3,3]`) rather than the raw `[[1,2,3,3]]` the judge runs.
 * Falls back to the raw text if it doesn't parse as one value per parameter.
 */
export function argumentLines(input: string, parameters: MethodParameter[]): string[] {
  try {
    const args: unknown = JSON.parse(input);
    if (Array.isArray(args) && args.length === parameters.length) {
      return orderedParameters(parameters).map((p, i) => `${p.name} = ${JSON.stringify(args[i])}`);
    }
  } catch {
    // not JSON — show as stored
  }
  return [input];
}

/**
 * The Run console's editable cases, one string per parameter (each a JSON value), pre-filled from the
 * problem's sample cases. Editing per parameter (`nums`, `target`) is friendlier than one raw
 * `[[...], 9]` array, and maps straight back with {@link toInputJson}.
 */
export function casesFromSamples(problem: Problem): string[][] {
  const count = problem.parameters.length;
  return problem.testCases.flatMap(tc => {
    try {
      const args: unknown = JSON.parse(tc.input);
      return Array.isArray(args) && args.length === count ? [args.map(a => JSON.stringify(a))] : [];
    } catch {
      return [];
    }
  });
}

/** One case's per-parameter values → the JSON argument array the backend runs (`[v1,v2]`). */
export function toInputJson(values: string[]): string {
  return `[${values.map(v => v.trim()).join(',')}]`;
}
