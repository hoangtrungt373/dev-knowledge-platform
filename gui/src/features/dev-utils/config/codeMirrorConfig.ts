import type { Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { css } from '@codemirror/lang-css';
import { html } from '@codemirror/lang-html';
import { javascript } from '@codemirror/lang-javascript';
import { json } from '@codemirror/lang-json';
import { less } from '@codemirror/lang-less';
import { php } from '@codemirror/lang-php';
import { sass } from '@codemirror/lang-sass';
import { sql } from '@codemirror/lang-sql';
import { xml } from '@codemirror/lang-xml';
import { yaml } from '@codemirror/lang-yaml';

// CodeMirror's own default content padding is much smaller than the `16px` (`p: 2`) inset every
// other Input/Output surface in `DevUtilToolPanel.tsx` already uses (the wrapping Box around the
// old plain `TextField`, the old `react-syntax-highlighter`'s own `padding: '16px'`) — without
// this, the code area would look noticeably tighter than everywhere else in the same panel. `&`
// targets `.cm-editor` itself (CodeMirror's own theme-selector convention) — the padding lives
// here, not on `.cm-content` alone, so the gutter (line numbers) and the text both get inset
// together as one unit, matching how the 16px inset used to apply to the *whole* old
// `TextField`/`react-syntax-highlighter` block rather than just its text. `.cm-editor` already has
// `box-sizing: border-box` from CodeMirror's own base theme, so adding padding here doesn't change
// its own outer size (which our own `height`/`minHeight`/`maxHeight` props still control) — only
// the room available for its content inside. The font-size change cascades down to the gutter/
// content text too, matching the `0.8rem` size the panel used before this feature switched to a
// real editor. Both this and `&.cm-focused` below are shared by both the Input and Output editors
// so neither can drift out of visual sync with the other.
//
// `&.cm-focused: { outline: 'none' }` removes CodeMirror's own default focused-state outline — a
// real, reported regression ("when I edit the Input box, there is an inner box show up (with dot
// border)"): `@codemirror/view`'s own base theme sets `&.cm-focused { outline: '1px dotted
// #212121' }` (confirmed by reading its own source, not guessed — its own comment there explains
// *why* it defaults to a visible dotted outline: `.cm-content` sits inside the scrollable
// container and doesn't include the gutters, so a browser's own native focus ring on the
// content-editable element alone wouldn't visually indicate the whole editor is focused; CodeMirror
// draws its own outline on `.cm-editor` instead to cover the gutters too). This app already frames
// both editors in their own bordered `Paper` card, so a second, inner dotted box reads as visual
// clutter rather than a helpful focus indicator — removed outright rather than restyled.
export const editorChromeTheme = EditorView.theme({
  '&': { fontSize: '0.8rem', padding: '16px' },
  '&.cm-focused': { outline: 'none' },
  '.cm-gutters': { paddingLeft: '4px' },
});

// Maps every language id this feature's own operations pass — both `OperationConfig.inputFormat`'s
// key set (`html`/`js`, matching the Input editor's own `inputFormat` prop) and
// `outputLanguages.ts#OUTPUT_LANGUAGE_INFO`'s key set (`markup`/`javascript`, matching Prism's own
// naming, still used for the info-row badge) — to the CodeMirror 6 language extension that
// highlights it. The two sets overlap but aren't identical (the same two languages are just named
// differently by each), so this map carries both spellings pointing at the same extension rather
// than normalizing one input shape into the other first.
//
// `erb`/`csv`/`text` all fall back to `[]` (CodeMirror's own plain-text rendering — still gets line
// numbers/bracket-matching from `basicSetup`, just no colorized syntax): ERB's `<% %>` templating
// has no maintained CodeMirror 6 package (falling back to `html()` was considered and rejected —
// that extension has no special handling for `<% %>` either, so it wouldn't add real highlighting
// value over plain text, just a different kind of incomplete parse), and CSV/plain text aren't
// really "languages" with syntax to highlight in the first place.
const LANGUAGE_EXTENSIONS: Record<string, () => Extension[]> = {
  json: () => [json()],
  yaml: () => [yaml()],
  markup: () => [html()],
  html: () => [html()],
  css: () => [css()],
  less: () => [less()],
  scss: () => [sass()],
  javascript: () => [javascript()],
  js: () => [javascript()],
  erb: () => [],
  xml: () => [xml()],
  csv: () => [],
  sql: () => [sql()],
  php: () => [php()],
  text: () => [],
};

/** Returns the CodeMirror 6 extension(s) that highlight the given language id, or `[]` for a
 * language with no dedicated grammar (see this file's own comment for which ones and why). Safe to
 * call with any string — an unrecognized id degrades to plain text rather than throwing, the same
 * "never guess, never crash on an unexpected value" posture this module's other lookups follow. */
export function getCodeMirrorExtensions(languageId: string): Extension[] {
  return LANGUAGE_EXTENSIONS[languageId]?.() ?? [];
}
