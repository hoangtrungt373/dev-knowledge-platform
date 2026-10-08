import { ParamType, ParsedSignature, Problem, ProblemPayload } from '../types';
import { orderedParameters } from './runCases';

/** One editable parameter row. `key` is a stable, client-only React key — rows are reordered and
 * removed, so the list index can't serve as one. */
export interface ParamRow {
  key: string;
  name: string;
  type: ParamType;
}

/** One editable test-case row; see {@link ParamRow} for `key`. */
export interface TestCaseRow {
  key: string;
  input: string;
  expectedOutput: string;
  sample: boolean;
}

/** The parts of a problem that make up its grading signature — frozen while it stays PUBLISHED. */
export interface Signature {
  methodName: string;
  returnType: ParamType;
  parameters: { name: string; type: ParamType }[];
}

/** A type parsed from a template whose spelling also fits other types (Python `int`, JS `number`). */
export interface TypeHint {
  chosen: ParamType;
  alternatives: ParamType[];
}

/** Parse hints by field: the return type, and each parameter by its row key. */
export interface SignatureTypeHints {
  returnType?: TypeHint;
  params: Record<string, TypeHint>;
}

/** Field-level form errors. Row errors are keyed by the row's own `key`. */
export interface ProblemFormErrors {
  title?: string;
  description?: string;
  /** Why the chosen status can't be saved (publishing an unverified contract). */
  status?: string;
  methodName?: string;
  signature?: string;
  parameters?: string;
  testCases?: string;
  paramNames: Record<string, string>;
  testCaseInputs: Record<string, string>;
  testCaseOutputs: Record<string, string>;
}

export const EMPTY_ERRORS: ProblemFormErrors = { paramNames: {}, testCaseInputs: {}, testCaseOutputs: {} };

// Same shape rule as dev-practice-service's SignatureNameValidator.IDENTIFIER_REGEX: ASCII letter
// first (a leading `_` is reserved for the generated harness's own locals). Fast inline feedback
// only — the backend additionally rejects each language's keywords and harness-owned names
// (e.g. `class`, `def`, `self`, `require`), which surface as the save's error toast rather than
// being duplicated here, so the keyword lists live in exactly one place.
const IDENTIFIER = /^[A-Za-z][A-Za-z0-9_]*$/;

let rowCounter = 0;
/** A fresh client-only row key. */
export function nextRowKey(): string {
  rowCounter += 1;
  return `row-${rowCounter}`;
}

export function signatureOf(methodName: string, returnType: ParamType, params: ParamRow[]): Signature {
  return {
    methodName: methodName.trim(),
    returnType,
    parameters: params.map(p => ({ name: p.name.trim(), type: p.type })),
  };
}

export function sameSignature(a: Signature, b: Signature): boolean {
  return (
    a.methodName === b.methodName &&
    a.returnType === b.returnType &&
    a.parameters.length === b.parameters.length &&
    a.parameters.every((p, i) => p.name === b.parameters[i].name && p.type === b.parameters[i].type)
  );
}

/**
 * A comparable fingerprint of the grading contract — the signature plus the ordered test data —
 * mirroring `ProblemServiceImpl#signatureChanged`/`#testDataChanged`: values are trimmed, and the
 * `sample` flag is left out (it changes what users see, not what counts as correct). Two forms with
 * the same fingerprint would leave the backend's `contractVersion` unchanged.
 */
export function contractFingerprint(signature: Signature, testCases: { input: string; expectedOutput: string }[]): string {
  return JSON.stringify([signature, testCases.map(t => [t.input.trim(), t.expectedOutput.trim()])]);
}

/**
 * Turns a parsed code template into fresh parameter rows plus the type guesses to flag: a type whose
 * template spelling also fits others (Python `int`, JavaScript `number`) gets a hint, keyed by the
 * new row's key. `guessCount` counts the return type too.
 */
export function signatureFromTemplate(parsed: ParsedSignature): {
  params: ParamRow[];
  hints: SignatureTypeHints;
  guessCount: number;
} {
  const params: ParamRow[] = parsed.parameters.map(p => ({ key: nextRowKey(), name: p.name, type: p.type }));
  const paramHints: Record<string, TypeHint> = {};
  parsed.parameters.forEach((p, i) => {
    if (p.alternatives.length > 0) paramHints[params[i].key] = { chosen: p.type, alternatives: p.alternatives };
  });
  const returnGuessed = parsed.returnTypeAlternatives.length > 0;
  return {
    params,
    hints: {
      returnType: returnGuessed ? { chosen: parsed.returnType, alternatives: parsed.returnTypeAlternatives } : undefined,
      params: paramHints,
    },
    guessCount: Object.keys(paramHints).length + (returnGuessed ? 1 : 0),
  };
}

/**
 * Why the form can't be saved as PUBLISHED right now, or `null` when it can — mirrors
 * `ProblemServiceImpl`'s publish rule: publishing needs an ACCEPTED reference solution at the
 * problem's current contract version, so the problem must be saved (a reference needs an id) and its
 * signature/test data must have no unsaved edits. A problem that's already published may stay
 * published as long as its contract isn't touched (problems published before DKP-0056 stay live
 * without a reference).
 *
 * @param saved the problem as last loaded/saved, or null in create mode
 * @param contractDirty whether the form holds signature/test-data edits the server hasn't seen
 */
export function publishBlockedReason(saved: Problem | null, contractDirty: boolean): string | null {
  if (!saved) return 'Create the problem as a Draft first, then run a reference solution to publish it.';
  if (contractDirty) {
    return saved.status === 'PUBLISHED'
      ? 'Changing the signature or test cases of a published problem needs re-verification: set the status to Draft, save, run a reference solution, then publish.'
      : 'Save the signature/test-case changes as a Draft and run a reference solution before publishing.';
  }
  if (saved.status !== 'PUBLISHED' && !saved.verified) {
    return 'Run a reference solution that passes every test case before publishing.';
  }
  return null;
}

/** Builds editable rows from a loaded problem (parameters sorted by their persisted position). */
export function rowsFromProblem(problem: Problem): { params: ParamRow[]; testCases: TestCaseRow[] } {
  return {
    params: orderedParameters(problem.parameters).map(p => ({ key: nextRowKey(), name: p.name, type: p.type })),
    testCases: problem.testCases.map(t => ({
      key: nextRowKey(),
      input: t.input,
      expectedOutput: t.expectedOutput,
      sample: t.sample,
    })),
  };
}

function parseJson(text: string): { ok: true; value: unknown } | { ok: false; message: string } {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : 'Invalid JSON' };
  }
}

/** Number of arguments a test-case input carries, or `null` if it isn't a JSON array. Used for
 * the inline "2 of 2 arguments" hint as well as validation. */
export function argumentCount(input: string): number | null {
  const parsed = parseJson(input);
  return parsed.ok && Array.isArray(parsed.value) ? parsed.value.length : null;
}

interface ValidateInput {
  title: string;
  description: string;
  methodName: string;
  params: ParamRow[];
  testCases: TestCaseRow[];
  /** True when the problem was loaded as PUBLISHED and is still set to PUBLISHED. */
  signatureLocked: boolean;
  /** The signature as last loaded from the server (edit mode only). */
  originalSignature: Signature | null;
  currentSignature: Signature;
  /** Set when the chosen status is PUBLISHED but the backend would refuse it — see
   * `ProblemFormPage#publishBlockedReason`. */
  publishBlockedReason?: string | null;
}

/**
 * Client-side mirror of the backend's own rules, so an admin sees problems inline before saving:
 * required fields, identifier-shaped names, unique parameter names, JSON-parseable test data, and
 * the two rules `ProblemServiceImpl` enforces (test-case arity, signature lock). The backend stays
 * the real guard; this is faster feedback.
 */
export function validateProblemForm(v: ValidateInput): ProblemFormErrors {
  const e: ProblemFormErrors = { paramNames: {}, testCaseInputs: {}, testCaseOutputs: {} };

  if (!v.title.trim()) e.title = 'Title is required';
  else if (v.title.trim().length > 255) e.title = 'Title must not exceed 255 characters';
  if (!v.description.trim()) e.description = 'Description is required';

  if (!v.methodName.trim()) e.methodName = 'Method name is required';
  else if (!IDENTIFIER.test(v.methodName.trim())) e.methodName = 'Use letters, digits and _, starting with a letter';

  if (v.params.length === 0) e.parameters = 'At least one parameter is required';
  const seen = new Set<string>();
  v.params.forEach(p => {
    const name = p.name.trim();
    if (!name) e.paramNames[p.key] = 'Required';
    else if (!IDENTIFIER.test(name)) e.paramNames[p.key] = 'Not a valid identifier';
    else if (seen.has(name)) e.paramNames[p.key] = 'Duplicate name';
    seen.add(name);
  });

  if (v.testCases.length === 0) e.testCases = 'At least one test case is required';
  v.testCases.forEach(t => {
    if (!t.input.trim()) {
      e.testCaseInputs[t.key] = 'Input is required';
    } else {
      const parsed = parseJson(t.input);
      if (!parsed.ok) e.testCaseInputs[t.key] = `Invalid JSON: ${parsed.message}`;
      else if (!Array.isArray(parsed.value)) e.testCaseInputs[t.key] = 'Input must be a JSON array of arguments';
      else if (parsed.value.length !== v.params.length) {
        e.testCaseInputs[t.key] = `Expected ${v.params.length} argument(s), got ${parsed.value.length}`;
      }
    }
    if (!t.expectedOutput.trim()) {
      e.testCaseOutputs[t.key] = 'Expected output is required';
    } else {
      const parsed = parseJson(t.expectedOutput);
      if (!parsed.ok) e.testCaseOutputs[t.key] = `Invalid JSON: ${parsed.message}`;
    }
  });

  if (v.signatureLocked && v.originalSignature && !sameSignature(v.originalSignature, v.currentSignature)) {
    e.signature = 'The signature of a published problem can\'t change. Revert it, or set the status to Draft in the same save.';
  }

  if (v.publishBlockedReason) e.status = v.publishBlockedReason;

  return e;
}

/** Every field the problem form can edit, in a plain shape both the form state and a saved
 * `Problem` can be turned into — the input to {@link formSnapshot}. */
export interface FormSnapshotFields {
  title: string;
  description: string;
  difficulty: Problem['difficulty'];
  status: Problem['status'];
  methodName: string;
  returnType: ParamType;
  parameters: { name: string; type: ParamType }[];
  testCases: { input: string; expectedOutput: string; sample: boolean }[];
  tagIds: number[];
  /** Tag names queued in the picker, not created yet — a change the admin would lose by leaving. */
  stagedTagNames: string[];
}

/**
 * A comparable string of everything the form edits, for the unsaved-changes guard. Normalized the
 * way `toPayload` sends it (trimmed text, tag ids order-independent), so only edits that would change
 * what Save sends count — retyping a value or adding a trailing space doesn't. Unlike
 * {@link contractFingerprint}, the `sample` flag *does* count here: it's still an unsaved edit.
 */
export function formSnapshot(f: FormSnapshotFields): string {
  return JSON.stringify({
    ...f,
    title: f.title.trim(),
    description: f.description.trim(),
    methodName: f.methodName.trim(),
    parameters: f.parameters.map(p => ({ name: p.name.trim(), type: p.type })),
    testCases: f.testCases.map(t => ({ input: t.input.trim(), expectedOutput: t.expectedOutput.trim(), sample: t.sample })),
    tagIds: [...f.tagIds].sort((a, b) => a - b),
  });
}

/** The snapshot fields of a saved problem — the form's "clean" state after a load or save. */
export function snapshotFieldsOf(problem: Problem): FormSnapshotFields {
  return {
    title: problem.title,
    description: problem.description,
    difficulty: problem.difficulty,
    status: problem.status,
    methodName: problem.methodName,
    returnType: problem.returnType,
    parameters: orderedParameters(problem.parameters).map(p => ({ name: p.name, type: p.type })),
    testCases: problem.testCases.map(t => ({ input: t.input, expectedOutput: t.expectedOutput, sample: t.sample })),
    tagIds: problem.tags.map(t => t.id),
    stagedTagNames: [],
  };
}

/** The problem form's main-column tabs, in display order. Also the `?tab=` URL values. */
export const FORM_TABS = ['details', 'signature', 'testCases', 'reference'] as const;
export type FormTab = (typeof FORM_TABS)[number];

export function isFormTab(value: string | null): value is FormTab {
  return value !== null && (FORM_TABS as readonly string[]).includes(value);
}

/**
 * Which tabs hold at least one field error, so a tab can flag errors the admin can't currently see.
 * `status` is absent on purpose: it lives in the always-visible sidebar, not in a tab.
 */
export function tabsWithErrors(e: ProblemFormErrors): Set<FormTab> {
  const tabs = new Set<FormTab>();
  if (e.title || e.description) tabs.add('details');
  if (e.methodName || e.signature || e.parameters || Object.keys(e.paramNames).length) tabs.add('signature');
  if (e.testCases || Object.keys(e.testCaseInputs).length || Object.keys(e.testCaseOutputs).length) {
    tabs.add('testCases');
  }
  return tabs;
}

/** The first tab (in display order) holding an error — where a failed save takes the admin. */
export function firstTabWithErrors(e: ProblemFormErrors): FormTab | null {
  const tabs = tabsWithErrors(e);
  return FORM_TABS.find(t => tabs.has(t)) ?? null;
}

export function hasErrors(e: ProblemFormErrors): boolean {
  return Boolean(
    e.title || e.description || e.status || e.methodName || e.signature || e.parameters || e.testCases ||
      Object.keys(e.paramNames).length ||
      Object.keys(e.testCaseInputs).length ||
      Object.keys(e.testCaseOutputs).length,
  );
}

export function toPayload(
  fields: Omit<ProblemPayload, 'parameters' | 'testCases'>,
  params: ParamRow[],
  testCases: TestCaseRow[],
): ProblemPayload {
  return {
    ...fields,
    title: fields.title.trim(),
    description: fields.description.trim(),
    methodName: fields.methodName.trim(),
    parameters: params.map(p => ({ name: p.name.trim(), type: p.type })),
    testCases: testCases.map(t => ({ input: t.input.trim(), expectedOutput: t.expectedOutput.trim(), sample: t.sample })),
  };
}
