import { ParamType, Problem, ProblemPayload } from '../types';

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

/** Field-level form errors. Row errors are keyed by the row's own `key`. */
export interface ProblemFormErrors {
  title?: string;
  description?: string;
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

/** Builds editable rows from a loaded problem (parameters sorted by their persisted position). */
export function rowsFromProblem(problem: Problem): { params: ParamRow[]; testCases: TestCaseRow[] } {
  return {
    params: [...problem.parameters]
      .sort((a, b) => a.position - b.position)
      .map(p => ({ key: nextRowKey(), name: p.name, type: p.type })),
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

  return e;
}

export function hasErrors(e: ProblemFormErrors): boolean {
  return Boolean(
    e.title || e.description || e.methodName || e.signature || e.parameters || e.testCases ||
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
