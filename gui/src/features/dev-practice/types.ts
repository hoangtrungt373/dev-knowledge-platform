// Mirrors dev-practice-service's own DTOs/enums field-for-field. Declared locally rather than
// imported from @content/types (which has its own ContentStatus) — dev-practice-service doesn't
// depend on content-service, and cross-feature type reuse here follows the backend's own
// dependency direction (see gui/CLAUDE.md).

/** `enums.Difficulty` — deliberately not content-service's BEGINNER/INTERMEDIATE/ADVANCED. */
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

/** `common.enums.ContentStatus`, as used by `Problem.status`. */
export type ProblemStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** `enums.ParamType` — the closed value-shape vocabulary every signature is restricted to. */
export type ParamType =
  | 'INT'
  | 'LONG'
  | 'DOUBLE'
  | 'BOOLEAN'
  | 'STRING'
  | 'INT_ARRAY'
  | 'DOUBLE_ARRAY'
  | 'BOOLEAN_ARRAY'
  | 'STRING_ARRAY'
  | 'INT_MATRIX';

export interface MethodParameter {
  name: string;
  type: ParamType;
  position: number;
}

export interface TestCase {
  id: number;
  /** JSON array of argument values, in parameter order — e.g. `[[2,7,11,15], 9]`. */
  input: string;
  /** One JSON value of the return type's shape — e.g. `[0,1]`. */
  expectedOutput: string;
  /** Shown publicly as a worked example; `false` means hidden, judge-only. */
  sample: boolean;
}

/** `ProblemTagSummaryResponse` — a tag as embedded in a problem response. */
export interface ProblemTagSummary {
  id: number;
  name: string;
  slug: string;
}

/** `ProblemTagResponse` — a tag as the admin Tags page sees it. */
export interface ProblemTag extends ProblemTagSummary {
  createdAt: string;
}

/** `ProblemResponse` — the admin get-by-id shape, including hidden test cases. */
export interface Problem {
  id: number;
  slug: string;
  title: string;
  description: string;
  difficulty: Difficulty;
  status: ProblemStatus;
  methodName: string;
  returnType: ParamType;
  parameters: MethodParameter[];
  testCases: TestCase[];
  /** Sorted by name. */
  tags: ProblemTagSummary[];
  publishedAt: string | null;
  createdAt: string;
}

/** `AdminProblemSummaryResponse` — one row of the admin list. */
export interface AdminProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  status: ProblemStatus;
  /** Sorted by name. */
  tags: ProblemTagSummary[];
  publishedAt: string | null;
  createdAt: string;
}

/** `CreateProblemRequest`/`UpdateProblemRequest` — identical shapes on the backend. Parameter
 * order is the list order; a parameter carries no `position` of its own on the way in. */
export interface ProblemPayload {
  title: string;
  description: string;
  difficulty: Difficulty;
  status: ProblemStatus;
  methodName: string;
  returnType: ParamType;
  parameters: { name: string; type: ParamType }[];
  testCases: { input: string; expectedOutput: string; sample: boolean }[];
  /** The complete tag set — the form always sends it, so update never relies on "omitted = unchanged". */
  tagIds: number[];
}
