export interface DevUtilError {
  /** Short, generic headline shown above the detail line — always "Cannot be processed" today. */
  headline: string;
  /** The actual diagnostic — a precise syntax-error message, ideally with a line/column. */
  detail: string;
}

const HEADLINE = 'Cannot be processed';

/**
 * Builds the friendly error shown inline in the Output panel.
 *
 * <p>**Backend (`dev-utils-service`) is now fixed to return a clean `errorMessage` itself** — its
 * `JsonFormatOperation`/`YamlToJsonOperation`/`JsonToYamlOperation` used to reuse
 * `JsonProcessingException.getMessage()` verbatim (leaking Jackson's own internal parser
 * diagnostics — `StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION`, `[Source: REDACTED (...)]` — per a
 * direct bug report), and `DevUtilsErrorCode.INVALID_JSON`/`INVALID_YAML`'s own `"Invalid {0}"`
 * templates were defined but never actually applied (a `BusinessException` overload-resolution
 * gotcha — see that class's own doc comment). Both are now fixed server-side: a new
 * `ParsingExceptionMessages.friendlyMessage(e)` strips the noise (using `getOriginalMessage()` +
 * the structured `getLocation()`, not string-parsing `getMessage()`) before the template is
 * applied via a `(Object)`-cast call that actually reaches `BusinessException`'s varargs
 * constructor — see `dev-utils-service/CLAUDE.md`'s note and each operation's own updated catch
 * block for the full fix. `HtmlBeautifyOperation` still has no failure path at all
 * (`Jsoup.parseBodyFragment` is a lenient parser that never throws), so `html-beautify` never
 * reaches an "invalid input" message either way — same for `css-beautify`/`less-beautify`/
 * `scss-beautify`/`js-beautify` (all four delegate to the equally lenient `CurlyBraceFormatter` on
 * the backend), `erb-beautify` (jsoup-based, same as HTML), `sql-format` (delegates to the
 * similarly lenient `SqlFormatter`), and `string-case-convert` (a pure text transform with no
 * notion of "invalid" input at all). The operations that *can* reach this path are `xml-beautify`
 * (`XmlOperation`, a real JAXP parser), `csv-to-json` (`CsvToJsonOperation`, a real Jackson
 * `CsvMapper` parser), and `php-to-json` (`PhpToJsonOperation`, this module's own
 * `PhpArrayParser`) — all three already return a clean `"<message> (line N, column M)"`-shaped
 * string of their own (see `dev-utils-service/CLAUDE.md`'s note), so all three take the same
 * `simplifyBackendMessage` fallback `yaml-to-json` already did — a pass-through in practice, per
 * that function's own idempotency guard. `json-to-csv`/`json-to-php` both reuse `INVALID_JSON`
 * server-side, so they're covered by the `isJsonInput` branch below like `json-format`/
 * `json-to-yaml` are, not this one.
 *
 * <p>This function still exists on the frontend for two reasons, not because the backend fix
 * didn't work: (1) for a JSON-input operation (`json-format`/`json-to-yaml`/`json-to-csv`/
 * `json-to-php`), the browser's own `JSON.parse` produces an even more precise, human-readable
 * syntax error (native V8 message, e.g. "Expected ',' or '}' after property value in JSON at
 * position 81 (line 1 column 82)") than Jackson's own phrasing ever could — reused verbatim
 * instead of asking the backend at all, since it's strictly better and doesn't need a round trip;
 * (2) `simplifyBackendMessage` stays as a defensive fallback for `yaml-to-json`/`xml-beautify`/
 * `csv-to-json`/`php-to-json` (no client-side parser available for any of the four) — normally a
 * pure passthrough of the now-already-clean backend message, but still tolerant of the old noisy
 * shape too, in case this ever regresses or a genuinely different technical-error message (network
 * failure, 5xx) reaches it instead. `url-string`/`base64-string`/`html-entity-string`'s own decode
 * actions, `php-serializer`'s own Unserialize action, and `hex-ascii`'s own Hex to ASCII action
 * take this same fallback path too — each uses `inputFormat: 'text'` (not `'json'`) since the
 * shared input box's actual content isn't JSON for at least one of the operation's two directions,
 * so `isJsonInput` is always `false` for them regardless of which action just ran.
 */
export function buildDevUtilError(input: string, isJsonInput: boolean, backendMessage: string): DevUtilError {
  if (isJsonInput) {
    try {
      JSON.parse(input);
    } catch (parseError) {
      if (parseError instanceof Error) {
        return { headline: HEADLINE, detail: parseError.message };
      }
    }
  }
  return { headline: HEADLINE, detail: simplifyBackendMessage(backendMessage) };
}

const LOCATION_SUFFIX = /\(line \d+, column \d+\)\s*$/;

/**
 * Strips any remaining parser-internals noise down to a plain, single-line
 * "<core sentence> (line N, column M)" — defensive today, not load-bearing, now that the backend
 * itself already returns exactly this shape (see `buildDevUtilError`'s own doc comment). Still
 * handles the pre-fix noisy shapes tolerantly (Jackson's own JSON-parser format and SnakeYAML's
 * scanner-level format, which differ in whether "line"/"column" carry a trailing colon and in what
 * marks the boundary between the human sentence and the location/source-snippet noise), and is
 * idempotent against an already-clean message — `core` is checked for a trailing "(line N, column
 * M)" before one gets appended, so a message the backend already cleaned up doesn't end up with
 * the same location suffix twice.
 */
function simplifyBackendMessage(raw: string): string {
  // Tolerates both "line: N, column: M" (Jackson) and "line N, column M" (SnakeYAML) — takes the
  // *last* match, since Jackson repeats the pair once per location it mentions (a start marker,
  // then the actual failure point) and the last one is always the real error location.
  const locationMatches = [...raw.matchAll(/line:?\s*(\d+),\s*column:?\s*(\d+)/gi)];
  const lastLocation = locationMatches[locationMatches.length - 1];

  // The core sentence is whatever precedes the first parser-internals marker — Jackson's own
  // "(start marker at [Source: ...])"/" at [Source: ...]" noise, or SnakeYAML's "\n in '...',
  // line N, column M:" trailer (plus the source-line snippet + "^" pointer that follows it). A
  // message the backend already cleaned up has none of these markers, so this is a no-op split.
  // Collapsing embedded newlines afterward folds a multi-line SnakeYAML explanation ("while
  // parsing a flow node\nexpected ...") into one readable line.
  const core = raw
    .split(/\s*\(start marker at|\s+at \[Source|\s*\n\s*in\s+'[^']*',/)[0]
    .replace(/\s+/g, ' ')
    .trim();

  if (!lastLocation || LOCATION_SUFFIX.test(core)) return core;
  return `${core} (line ${lastLocation[1]}, column ${lastLocation[2]})`;
}
