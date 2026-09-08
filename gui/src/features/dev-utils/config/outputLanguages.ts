// Single source of truth for "output language id -> display label + badge color", replacing what
// used to be two independently-maintained parallel Record<string, string> maps in
// DevUtilToolPanel.tsx — nothing enforced those two ever having the same key set, so adding a
// color for a new language and forgetting its label (or vice versa) was an easy, silent mistake.
// One map makes that impossible: every entry always carries both.
//
// `OutputLanguage` (below) is derived from this map's own keys, so `OperationConfig.outputLanguage`
// (config/operations.tsx) is a real, checked literal union instead of a loose `string` — a typo'd
// language id that isn't a real key here is now a compile error, not a silent fallback to a
// generic badge at runtime.
//
// 'xml' is its own key, not folded into 'markup' — Prism/refractor's `markup` grammar registers
// 'xml' as one of its own aliases (so syntax highlighting still works), but this app-level lookup
// is keyed on the exact string each operation passes, so XML gets its own label/color distinct
// from HTML rather than silently reading as "HTML" too. 'text' (String Case Converter's own
// output) has no real Prism grammar to highlight against — it renders unstyled, which is exactly
// right for a block of labelled plain-text lines — but still gets a real label/color here so its
// info-row badge doesn't fall back to showing the raw string "text".
//
// Colors: common language-badge hues, each bright enough to stay readable against both
// DevUtilToolPanel's OUTPUT_INFO_BG and OUTPUT_BG_DARK — JSON blue, YAML purple, HTML orange, CSS
// blue, LESS indigo, SCSS pink (Sass's own brand color), JS yellow, ERB Ruby-red, XML teal, CSV
// green (spreadsheet), SQL amber, PHP's own brand indigo, TEXT neutral grey (it isn't really a
// "language").
export const OUTPUT_LANGUAGE_INFO = {
  json: { label: 'JSON', color: '#4fc1ff' },
  yaml: { label: 'YAML', color: '#c586c0' },
  markup: { label: 'HTML', color: '#e37933' },
  css: { label: 'CSS', color: '#42a5f5' },
  less: { label: 'LESS', color: '#5a67d8' },
  scss: { label: 'SCSS', color: '#cf649a' },
  javascript: { label: 'JS', color: '#f0db4f' },
  erb: { label: 'ERB', color: '#cc342d' },
  xml: { label: 'XML', color: '#4ec9b0' },
  csv: { label: 'CSV', color: '#8bc34a' },
  sql: { label: 'SQL', color: '#dcb67a' },
  php: { label: 'PHP', color: '#8892bf' },
  text: { label: 'TEXT', color: '#cccccc' },
} as const satisfies Record<string, { label: string; color: string }>;

export type OutputLanguage = keyof typeof OUTPUT_LANGUAGE_INFO;
