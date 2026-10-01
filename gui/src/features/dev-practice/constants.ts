import { Difficulty, ParamType, ProblemStatus } from './types';

// Shared by the list page (chips/filters) and the form page (selects), so a value never reads
// differently between the two.

export const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];
export const STATUSES: ProblemStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];

export const DIFFICULTY_LABEL: Record<Difficulty, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
export const STATUS_LABEL: Record<ProblemStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
};

export const DIFFICULTY_COLOR: Record<Difficulty, 'success' | 'warning' | 'error'> = {
  EASY: 'success',
  MEDIUM: 'warning',
  HARD: 'error',
};
export const STATUS_COLOR: Record<ProblemStatus, 'default' | 'success' | 'warning'> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  ARCHIVED: 'warning',
};

/** Every `ParamType`, in the backend enum's own declaration order, with a human label and a JSON
 * example of one value — shown in the type picker so an admin knows how to write test data. */
export const PARAM_TYPES: { value: ParamType; label: string; example: string }[] = [
  { value: 'INT', label: 'int', example: '42' },
  { value: 'LONG', label: 'long', example: '9007199254740991' },
  { value: 'DOUBLE', label: 'double', example: '3.14' },
  { value: 'BOOLEAN', label: 'boolean', example: 'true' },
  { value: 'STRING', label: 'string', example: '"hello"' },
  { value: 'INT_ARRAY', label: 'int[]', example: '[1, 2, 3]' },
  { value: 'DOUBLE_ARRAY', label: 'double[]', example: '[1.5, 2.0]' },
  { value: 'BOOLEAN_ARRAY', label: 'boolean[]', example: '[true, false]' },
  { value: 'STRING_ARRAY', label: 'string[]', example: '["a", "b"]' },
  { value: 'INT_MATRIX', label: 'int[][]', example: '[[1, 2], [3, 4]]' },
];

export const PARAM_TYPE_LABEL: Record<ParamType, string> = Object.fromEntries(
  PARAM_TYPES.map(t => [t.value, t.label]),
) as Record<ParamType, string>;
