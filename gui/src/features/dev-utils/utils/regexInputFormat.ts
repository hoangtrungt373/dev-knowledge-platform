export interface RegexTesterFields {
  pattern: string;
  flags: string;
  testText: string;
}

/**
 * Serializes/deserializes RegExp Tester's 3 logically distinct fields (pattern, flags, test text)
 * into the single lifted `input` string `DevUtilsPage.tsx`'s Sample/Clear buttons operate on — the
 * same "one shared lifted string, several visual widgets" trick `Base64ImagePanel.tsx` already
 * establishes for its own Upload/Data-URL-box pair. Shared between `components/
 * RegExpTesterPanel.tsx` (which renders the 3 fields, reconstructing this string on every edit)
 * and `config/operations.tsx` (whose `onSubmit` decomposes the same string back apart before
 * calling `devUtilsApi.testRegexp`), so the format is only ever defined in one place.
 *
 * <p>Format: `/{pattern}/{flags}` (a JS-style regex literal) on the first line, a blank line, then
 * the test text. Safe to round-trip because a pattern can never itself contain a real newline — it
 * is always typed into a plain single-line `TextField` — so the first line is always exactly the
 * delimited pattern+flags, however many lines the test text below it spans.
 */
export function serializeRegexInput({ pattern, flags, testText }: RegexTesterFields): string {
  return `/${pattern}/${flags}\n\n${testText}`;
}

/**
 * The inverse of {@link serializeRegexInput} — tolerant of anything that doesn't already match
 * that exact shape (e.g. `DevUtilsPage.tsx`'s Clear button setting `input` to `''`, or a bare
 * pattern pasted with no delimiters at all): falls back to treating the whole first line as the
 * pattern with no flags, rather than discarding it.
 */
export function parseRegexInput(raw: string): RegexTesterFields {
  const newlineIndex = raw.indexOf('\n');
  const firstLine = newlineIndex === -1 ? raw : raw.slice(0, newlineIndex);
  // Drops exactly one blank-line separator, matching what serializeRegexInput itself writes — a
  // second/third blank line (or none at all, e.g. a hand-edited value) is left as part of the test
  // text itself rather than stripped further.
  const rest = newlineIndex === -1 ? '' : raw.slice(newlineIndex + 1).replace(/^\n/, '');

  const lastSlash = firstLine.startsWith('/') ? firstLine.lastIndexOf('/') : -1;
  if (lastSlash > 0) {
    return { pattern: firstLine.slice(1, lastSlash), flags: firstLine.slice(lastSlash + 1), testText: rest };
  }
  return { pattern: firstLine, flags: '', testText: rest };
}
