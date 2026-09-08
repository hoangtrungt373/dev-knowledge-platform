import DataObjectIcon from '@mui/icons-material/DataObjectOutlined';
import SwapHorizIcon from '@mui/icons-material/SwapHorizOutlined';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHighOutlined';
import TableChartIcon from '@mui/icons-material/TableChartOutlined';
import StorageIcon from '@mui/icons-material/StorageOutlined';
import PhpIcon from '@mui/icons-material/PhpOutlined';
import AbcIcon from '@mui/icons-material/AbcOutlined';

import { devUtilsApi } from '../api/devUtilsApi';
import { DevUtilsResponse, StringCaseResponse } from '../types';
import { OutputLanguage } from './outputLanguages';

export type TabKey =
  | 'json-format'
  | 'yaml-to-json'
  | 'json-to-yaml'
  | 'html-beautify'
  | 'css-beautify'
  | 'less-beautify'
  | 'scss-beautify'
  | 'js-beautify'
  | 'erb-beautify'
  | 'xml-beautify'
  | 'json-to-csv'
  | 'csv-to-json'
  | 'sql-format'
  | 'php-to-json'
  | 'json-to-php'
  | 'string-case-convert';

export const TAB_KEYS: TabKey[] = [
  'json-format',
  'yaml-to-json',
  'json-to-yaml',
  'html-beautify',
  'css-beautify',
  'less-beautify',
  'scss-beautify',
  'js-beautify',
  'erb-beautify',
  'xml-beautify',
  'json-to-csv',
  'csv-to-json',
  'sql-format',
  'php-to-json',
  'json-to-php',
  'string-case-convert',
];

export const DEFAULT_TAB: TabKey = 'json-format';

export function tabFromHash(hash: string): TabKey {
  const key = hash.replace(/^#/, '');
  return (TAB_KEYS as string[]).includes(key) ? (key as TabKey) : DEFAULT_TAB;
}

export interface OperationConfig {
  key: TabKey;
  /** Sidebar group label, also shown as the eyebrow line above the Input/Output panels — e.g.
   * "Formatters" vs. "Converters" vs. "Text Tools". Purely descriptive today (the sidebar list
   * itself stays flat, not grouped into sections) — group it visually too if the operation count
   * grows enough to warrant it. */
  category: string;
  label: string;
  /** One-line summary shown above the Input/Output panels, under the operation's own title. */
  description: string;
  icon: JSX.Element;
  actionLabel: string;
  /** Doubles as both the empty-textarea ghost text and the value the headline card's Sample
   * button fills in — unified into one field per request, after `sampleInput` had briefly existed
   * as a separate, richer field (see gui/CLAUDE.md's dev-utils section for that reversal's own
   * history). A realistic, mixed-type example, not a minimal one, since it now has to do both jobs
   * at once — and, importantly, an actual *example of input to convert*, not a restatement of
   * `description` (a real bug once slipped in here for `string-case-convert`: its placeholder was
   * literally the description text, which meant "Sample" filled the input with a sentence
   * describing the tool instead of demonstrating it). */
  inputPlaceholder: string;
  /** What format the *input* box holds. Only ever actually branched on for the literal `'json'`
   * (picks `errorFormatting.ts#buildDevUtilError`'s client-side `JSON.parse` fast path) — every
   * other value just takes that function's generic backend-message fallback. See that function's
   * own doc comment for exactly which operations' backend can genuinely reject their input (and
   * therefore ever actually populate that fallback) — not re-derived here, to avoid the same fact
   * drifting out of sync across multiple files' comments the way it once did. */
  inputFormat: 'json' | 'yaml' | 'html' | 'css' | 'less' | 'scss' | 'js' | 'erb' | 'xml' | 'csv' | 'sql' | 'php' | 'text';
  /** Prism language for the output syntax highlighter — also the key into
   * `config/outputLanguages.ts#OUTPUT_LANGUAGE_INFO` for the Output panel's info-row badge
   * (label + color), so this is always a real, known language id, checked at compile time. */
  outputLanguage: OutputLanguage;
  supportsMinify: boolean;
  /** Filename offered by the Output panel's Download button. */
  downloadFileName: string;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
}

// String Case Converter is the one operation whose backend response (StringCaseResponse) isn't a
// single string — every other operation's `onSubmit` returns `Promise<DevUtilsResponse>`
// (`{ output: string }`), which `DevUtilToolPanel` renders as one syntax-highlighted block. Rather
// than building a second, parallel result-rendering path just for this one operation, its own
// `onSubmit` (below) formats the 7 case variants into that same "output" shape — one
// "<Label>\n<value>" pair per variant, blank-line separated — reusing the entire existing
// Input/Output panel (copy/download/etc.) for free. This is also exactly the plain-text layout a
// case-converter tool's own output conventionally takes.
function formatStringCaseResult(result: StringCaseResponse): string {
  const variants: Array<[string, string]> = [
    ['camelCase', result.camelCase],
    ['PascalCase', result.pascalCase],
    ['snake_case', result.snakeCase],
    ['kebab-case', result.kebabCase],
    ['CONSTANT_CASE', result.constantCase],
    ['Title Case', result.titleCase],
    ['Sentence case', result.sentenceCase],
  ];
  return variants.map(([label, value]) => `${label}\n${value}`).join('\n\n');
}

// A plain module-level constant, not built inside `DevUtilsPage`'s own render — nothing in here
// closes over component state/props (every `onSubmit` is either a direct `devUtilsApi.*` method
// reference or a small standalone wrapper), so there's no reason to rebuild this array on every
// render the way it used to be when it lived inline in that component.
export const OPERATIONS: OperationConfig[] = [
  {
    key: 'json-format',
    category: 'Formatters',
    label: 'JSON Format/Validate',
    description: 'Beautify, minify, and validate JSON',
    icon: <DataObjectIcon fontSize="small" />,
    actionLabel: 'Format',
    inputPlaceholder: '{"project":"Vui Coding","online":true,"tools":["JSON","Base64","JWT"],"stars":128}',
    inputFormat: 'json',
    outputLanguage: 'json',
    supportsMinify: true,
    downloadFileName: 'formatted.json',
    onSubmit: devUtilsApi.formatJson,
  },
  {
    key: 'yaml-to-json',
    category: 'Converters',
    label: 'YAML to JSON',
    description: 'Convert YAML documents into JSON',
    icon: <SwapHorizIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: 'project: Vui Coding\nonline: true\ntools:\n  - JSON\n  - Base64\n  - JWT\nstars: 128\n',
    inputFormat: 'yaml',
    outputLanguage: 'json',
    supportsMinify: true,
    downloadFileName: 'converted.json',
    onSubmit: devUtilsApi.yamlToJson,
  },
  {
    key: 'json-to-yaml',
    category: 'Converters',
    label: 'JSON to YAML',
    description: 'Convert JSON documents into YAML',
    icon: <SwapHorizIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: '{"project":"Vui Coding","online":true,"tools":["JSON","Base64","JWT"],"stars":128}',
    inputFormat: 'json',
    outputLanguage: 'yaml',
    // No minify option here — jackson-dataformat-yaml has no single-line/flow-style toggle, so
    // devUtilsApi.jsonToYaml doesn't even accept the parameter (see its own comment).
    supportsMinify: false,
    downloadFileName: 'converted.yaml',
    onSubmit: input => devUtilsApi.jsonToYaml(input),
  },
  {
    key: 'html-beautify',
    category: 'Formatters',
    label: 'HTML Beautify',
    description: 'Beautify or minify HTML markup',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder:
      '<div class="card"><h2>Vui Coding</h2><p>Online: <strong>true</strong></p><ul><li>JSON</li><li>Base64</li><li>JWT</li></ul></div>',
    inputFormat: 'html',
    outputLanguage: 'markup',
    supportsMinify: true,
    downloadFileName: 'beautified.html',
    onSubmit: devUtilsApi.beautifyHtml,
  },
  {
    key: 'css-beautify',
    category: 'Formatters',
    label: 'CSS Beautify/Minify',
    description: 'Beautify or minify CSS stylesheets',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder: '.card{background:#fff;padding:16px;}.card h2{color:#333;font-size:20px;}/* Vui Coding */',
    inputFormat: 'css',
    outputLanguage: 'css',
    supportsMinify: true,
    downloadFileName: 'beautified.css',
    onSubmit: devUtilsApi.beautifyCss,
  },
  {
    key: 'js-beautify',
    category: 'Formatters',
    label: 'JS Beautify/Minify',
    description: 'Beautify or minify JavaScript code',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder:
      'function describe(project){if(project.online){return project.name+" has "+project.stars+" stars";}return null;}',
    inputFormat: 'js',
    outputLanguage: 'javascript',
    supportsMinify: true,
    downloadFileName: 'beautified.js',
    onSubmit: devUtilsApi.beautifyJs,
  },
  {
    key: 'erb-beautify',
    category: 'Formatters',
    label: 'ERB Beautify/Minify',
    description: 'Beautify or minify ERB (Embedded RuBy) templates',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder: '<div class="card"><h2><%= project.name %></h2><% if project.online %><p>Online</p><% end %></div>',
    inputFormat: 'erb',
    outputLanguage: 'erb',
    supportsMinify: true,
    downloadFileName: 'beautified.erb',
    onSubmit: devUtilsApi.beautifyErb,
  },
  {
    key: 'less-beautify',
    category: 'Formatters',
    label: 'LESS Beautify/Minify',
    description: 'Beautify or minify LESS stylesheets',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder:
        '@primary: #333; // Vui Coding\n.card{background:#fff;padding:16px;h2{color:@primary;font-size:20px;}}',
    inputFormat: 'less',
    outputLanguage: 'less',
    supportsMinify: true,
    downloadFileName: 'beautified.less',
    onSubmit: devUtilsApi.beautifyLess,
  },
  {
    key: 'scss-beautify',
    category: 'Formatters',
    label: 'SCSS Beautify/Minify',
    description: 'Beautify or minify SCSS stylesheets',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder:
        '$primary: #333; // Vui Coding\n.card{background:#fff;padding:16px;h2{color:$primary;font-size:20px;}}',
    inputFormat: 'scss',
    outputLanguage: 'scss',
    supportsMinify: true,
    downloadFileName: 'beautified.scss',
    onSubmit: devUtilsApi.beautifyScss,
  },
  {
    key: 'xml-beautify',
    category: 'Formatters',
    label: 'XML Beautify/Minify',
    description: 'Validate, beautify, or minify XML documents',
    icon: <AutoFixHighIcon fontSize="small" />,
    actionLabel: 'Beautify',
    inputPlaceholder:
      '<project><name>Vui Coding</name><online>true</online><tools><tool>JSON</tool><tool>Base64</tool></tools></project>',
    inputFormat: 'xml',
    outputLanguage: 'xml',
    supportsMinify: true,
    downloadFileName: 'beautified.xml',
    onSubmit: devUtilsApi.beautifyXml,
  },
  {
    key: 'json-to-csv',
    category: 'Converters',
    label: 'JSON to CSV',
    description: 'Convert a JSON array of objects into CSV',
    icon: <TableChartIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: '[{"tool":"JSON","stars":128},{"tool":"Base64","stars":64},{"tool":"JWT","stars":32}]',
    inputFormat: 'json',
    outputLanguage: 'csv',
    // No minify option here — CSV has no distinct "compact" form, same reasoning JSON to YAML
    // has none (see devUtilsApi.jsonToCsv's own comment).
    supportsMinify: false,
    downloadFileName: 'converted.csv',
    onSubmit: input => devUtilsApi.jsonToCsv(input),
  },
  {
    key: 'csv-to-json',
    category: 'Converters',
    label: 'CSV to JSON',
    description: 'Convert CSV (first row as header) into a JSON array of objects',
    icon: <TableChartIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: 'tool,stars\nJSON,128\nBase64,64\nJWT,32\n',
    inputFormat: 'csv',
    outputLanguage: 'json',
    supportsMinify: true,
    downloadFileName: 'converted.json',
    onSubmit: devUtilsApi.csvToJson,
  },
  {
    key: 'sql-format',
    category: 'Formatters',
    label: 'SQL Format/Minify',
    description: 'Format or minify a SQL query',
    icon: <StorageIcon fontSize="small" />,
    actionLabel: 'Format',
    inputPlaceholder: 'select tool, stars from tools where stars > 50 order by stars desc',
    inputFormat: 'sql',
    outputLanguage: 'sql',
    supportsMinify: true,
    downloadFileName: 'formatted.sql',
    onSubmit: devUtilsApi.formatSql,
  },
  {
    key: 'php-to-json',
    category: 'Converters',
    label: 'PHP to JSON',
    description: 'Convert a PHP array literal into JSON',
    icon: <PhpIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: "['tool' => 'JSON', 'stars' => 128, 'tags' => ['JSON', 'JWT']]",
    inputFormat: 'php',
    outputLanguage: 'json',
    supportsMinify: true,
    downloadFileName: 'converted.json',
    onSubmit: devUtilsApi.phpToJson,
  },
  {
    key: 'json-to-php',
    category: 'Converters',
    label: 'JSON to PHP',
    description: 'Convert JSON into a PHP array literal',
    icon: <PhpIcon fontSize="small" />,
    actionLabel: 'Convert',
    inputPlaceholder: '{"tool":"JSON","stars":128,"tags":["JSON","JWT"]}',
    inputFormat: 'json',
    outputLanguage: 'php',
    supportsMinify: true,
    downloadFileName: 'converted.php',
    onSubmit: devUtilsApi.jsonToPhp,
  },
  {
    key: 'string-case-convert',
    // Neither a beautify/minify Formatter nor a format-A-to-format-B Converter — a dedicated
    // third category for this one, rather than stretching either existing label to cover it.
    category: 'Text Tools',
    label: 'String Case Converter',
    description: 'Convert text into camelCase, PascalCase, snake_case, kebab-case, and more',
    icon: <AbcIcon fontSize="small" />,
    actionLabel: 'Convert',
    // A real example to convert, not a restatement of `description` above (see that field's own
    // doc comment for the bug this once was).
    inputPlaceholder: 'Build ship and share with Vui Coding',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — there's no "compact form" of a case conversion (see
    // devUtilsApi.convertStringCase's own comment).
    supportsMinify: false,
    downloadFileName: 'string-case.txt',
    onSubmit: async input => ({ output: formatStringCaseResult(await devUtilsApi.convertStringCase(input)) }),
  },
];
