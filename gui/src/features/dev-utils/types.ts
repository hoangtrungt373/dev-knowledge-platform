export interface DevUtilsResponse {
  output: string;
}

// Mirrors dev-utils-service's own dto.StringCaseResponse field-for-field — the one operation in
// this feature whose output is genuinely richer than a single string, so it gets its own type
// rather than being forced into DevUtilsResponse (see that backend record's own Javadoc).
export interface StringCaseResponse {
  camelCase: string;
  pascalCase: string;
  snakeCase: string;
  kebabCase: string;
  constantCase: string;
  titleCase: string;
  sentenceCase: string;
}

// Mirrors dev-utils-service's own dto.HashResponse field-for-field — the second operation (after
// StringCaseResponse) whose output is genuinely richer than a single string.
export interface HashResponse {
  sha1: string;
  sha256: string;
  sha384: string;
  sha512: string;
}

// Mirrors dev-utils-service's own dto.TextDiffResponse.DiffLineType enum exactly — a plain string
// union, not a TS enum, matching how every other backend enum crossing this API boundary
// (OperationGroupName's own backend counterpart, etc.) is represented on this side.
export type DiffLineType = 'CONTEXT' | 'ADDED' | 'REMOVED';

// Mirrors dev-utils-service's own dto.TextDiffResponse.DiffLine record field-for-field.
export interface DiffLine {
  type: DiffLineType;
  text: string;
}

// Mirrors dev-utils-service's own dto.TextDiffResponse field-for-field — the third operation
// (after StringCaseResponse/HashResponse) whose output is genuinely richer than a single string;
// `lines` is real structured data (never re-parsed from a `-`/`+`-prefixed text block) so
// `components/TextDiffPanel.tsx` can render each line's own color/emphasis directly.
export interface TextDiffResponse {
  lines: DiffLine[];
  addedCount: number;
  removedCount: number;
  unchangedCount: number;
}
