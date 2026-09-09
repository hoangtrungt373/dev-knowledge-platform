import DataObjectIcon from '@mui/icons-material/DataObjectOutlined';
import SwapHorizIcon from '@mui/icons-material/SwapHorizOutlined';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHighOutlined';
import TableChartIcon from '@mui/icons-material/TableChartOutlined';
import StorageIcon from '@mui/icons-material/StorageOutlined';
import PhpIcon from '@mui/icons-material/PhpOutlined';
import AbcIcon from '@mui/icons-material/AbcOutlined';
import CodeIcon from '@mui/icons-material/CodeOutlined';
import LinkIcon from '@mui/icons-material/LinkOutlined';
import HtmlEntityIcon from '@mui/icons-material/HtmlOutlined';
import FingerprintIcon from '@mui/icons-material/FingerprintOutlined';
import HexIcon from '@mui/icons-material/HexagonOutlined';
import ImageIcon from '@mui/icons-material/ImageOutlined';

import { devUtilsApi } from '../api/devUtilsApi';
import { DevUtilsResponse, HashResponse, StringCaseResponse } from '../types';
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
  | 'string-case-convert'
  | 'base64-string'
  | 'url-string'
  | 'html-entity-string'
  | 'hash-generator'
  | 'php-serializer'
  | 'hex-ascii'
  | 'base64-image';

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
  'base64-string',
  'url-string',
  'html-entity-string',
  'hash-generator',
  'php-serializer',
  'hex-ascii',
  'base64-image',
];

export const DEFAULT_TAB: TabKey = 'json-format';

export function tabFromHash(hash: string): TabKey {
  const key = hash.replace(/^#/, '');
  return (TAB_KEYS as string[]).includes(key) ? (key as TabKey) : DEFAULT_TAB;
}

/** Mirrors the backend's own `service.OperationGroup` enum (`dev-utils-service`) — a much broader
 * clustering than `OperationConfig.category` below, meant to span the whole page rather than one
 * operation's own headline card. `'Formatters'` is the only group any operation actually declares
 * today; the other four are declared ahead of the operations that will eventually use them (an
 * encoder/decoder, an inspector, a web-specific tool, a generator — see the backend enum's own
 * Javadoc for a real example of each), so `OPERATION_GROUP_ORDER`/`groupOperationsByGroup` below
 * already have a stable, complete section order to render from day one. */
export type OperationGroupName = 'Formatters' | 'Encoders/Decoders' | 'Inspectors' | 'Web' | 'Generators';

/** Fixed rendering order for the sidebar's own group headlines — mirrors the backend enum's own
 * declaration order exactly, so a future group's sidebar section always appears in the same place
 * regardless of where its operations happen to sit in the `OPERATIONS` array below. */
export const OPERATION_GROUP_ORDER: OperationGroupName[] = [
  'Formatters',
  'Encoders/Decoders',
  'Inspectors',
  'Web',
  'Generators',
];

export interface OperationConfig {
  key: TabKey;
  /** Which page-level sidebar section this operation's own entry renders under — see
   * `OperationGroupName`'s own doc comment. */
  group: OperationGroupName;
  /** Sidebar group label, also shown as the eyebrow line above the Input/Output panels — e.g.
   * "Formatters" vs. "Converters" vs. "Text Tools". A finer-grained, per-`group`-internal
   * subdivision than `group` itself (today, every operation's own `group` is `'Formatters'`, but
   * their `category` still varies between "Formatters"/"Converters"/"Text Tools") — purely
   * descriptive, the sidebar list within one group stays flat, not further subdivided into its own
   * sub-sections. */
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
  /** Optional — every operation that renders through the shared `DevUtilToolPanel` always
   * provides one (that component's own `onSubmit` prop is required, not optional); a custom-panel
   * operation with no backend round trip at all (`base64-image` — converting an uploaded file to
   * a Data URL is a pure browser `FileReader` operation, nothing for a REST endpoint to do) simply
   * omits it rather than supplying a dead placeholder function nothing would ever call. */
  onSubmit?: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  /** A second, independent action button next to the primary one — e.g. Base64's own "Encode"/
   * "Decode" pair. Omitted entirely for every operation with only one action. See
   * `DevUtilToolPanel.tsx`'s own doc comment for the full reasoning (including why this isn't a
   * generalized N-action array). */
  secondaryAction?: {
    label: string;
    onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
  };
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

// Hash Generator is the second operation whose backend response (HashResponse) isn't a single
// string — same "format the named variants into one plain-text block" trick
// formatStringCaseResult already establishes above, reused verbatim rather than building a second
// parallel result-rendering path.
function formatHashResult(result: HashResponse): string {
  const variants: Array<[string, string]> = [
    ['SHA-1', result.sha1],
    ['SHA-256', result.sha256],
    ['SHA-384', result.sha384],
    ['SHA-512', result.sha512],
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    group: 'Formatters',
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
    key: 'string-case-convert',
    // Neither a beautify/minify Formatter nor a format-A-to-format-B Converter — a dedicated
    // third category for this one, rather than stretching either existing label to cover it.
    group: 'Formatters',
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
  {
    key: 'php-to-json',
    group: 'Formatters',
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
    group: 'Formatters',
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
    key: 'base64-string',
    // The first operation outside the Formatters group — see OperationGroup.java's own Javadoc,
    // which named exactly this ("a Base64/URL encoder for ENCODERS_DECODERS") as the concrete
    // example that group was declared ahead of use for.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    label: 'Base64 String',
    description: 'Encode and decode Base64 strings',
    icon: <CodeIcon fontSize="small" />,
    actionLabel: 'Encode',
    inputPlaceholder: 'Encode and decode Base64 strings',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — a Base64 encoding has no distinct "compact form" to toggle, same
    // reasoning `json-to-yaml`/`json-to-csv` already establish.
    supportsMinify: false,
    downloadFileName: 'base64.txt',
    onSubmit: input => devUtilsApi.encodeBase64(input),
    // Encode/Decode are two fully independent actions over the same input, not a base/compact-form
    // pair — see DevUtilToolPanel.tsx's own `secondaryAction` doc comment for why this needs a
    // second real action button rather than being squeezed into the Minify toggle's shape.
    secondaryAction: {
      label: 'Decode',
      onSubmit: input => devUtilsApi.decodeBase64(input),
    },
  },
  {
    key: 'url-string',
    // The second ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
    // UrlEncodeOperation/UrlDecodeOperation — same Encode/Decode pairing shape base64-string
    // already established.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    label: 'URL Encode/Decode',
    description: 'Encode and decode URL strings',
    icon: <LinkIcon fontSize="small" />,
    actionLabel: 'Encode',
    inputPlaceholder: 'https://translate.google.com/?hl=vi&sl=vi&tl=en&op=translate',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — a percent-encoding has no distinct "compact form" to toggle, same
    // reasoning `base64-string` already establishes.
    supportsMinify: false,
    downloadFileName: 'url-encoded.txt',
    onSubmit: input => devUtilsApi.encodeUrl(input),
    secondaryAction: {
      label: 'Decode',
      onSubmit: input => devUtilsApi.decodeUrl(input),
    },
  },
  {
    key: 'html-entity-string',
    // The third ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
    // HtmlEntityEncodeOperation/HtmlEntityDecodeOperation — same Encode/Decode pairing shape
    // base64-string/url-string already established.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    label: 'HTML Entity',
    description: 'Encode and decode HTML entities',
    icon: <HtmlEntityIcon fontSize="small" />,
    actionLabel: 'Encode',
    inputPlaceholder: '<main class="hero">Dev Knowledge Platform © 2026</main>',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — an escaped form has no distinct "compact form" to toggle, same reasoning
    // `base64-string`/`url-string` already establish.
    supportsMinify: false,
    downloadFileName: 'html-entity.txt',
    onSubmit: input => devUtilsApi.encodeHtmlEntity(input),
    secondaryAction: {
      label: 'Decode',
      onSubmit: input => devUtilsApi.decodeHtmlEntity(input),
    },
  },
  {
    key: 'hash-generator',
    // Moved here from 'Inspectors', per direct request — a hash digest is arguably closer to a
    // one-way encoding of a value than an "inspection" of one, and this keeps every non-Formatters
    // text-transform operation (Base64/URL/HTML Entity/PHP Serializer/Hash) under one sidebar
    // section. See OperationGroup.java's own updated Javadoc for the backend side of this move —
    // 'Inspectors' is back to fully declared-ahead-of-use (a future JWT decoder) as a result.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    label: 'Hash Generator',
    description: 'Generate SHA-1, SHA-256, SHA-384, and SHA-512 hashes',
    icon: <FingerprintIcon fontSize="small" />,
    actionLabel: 'Generate',
    inputPlaceholder: 'DevKnowledge — Build, Ship, Share',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — a hash digest has no distinct "compact form" to toggle, same reasoning
    // `base64-string`/`url-string`/`html-entity-string` already establish.
    supportsMinify: false,
    downloadFileName: 'hashes.txt',
    // Same "format the richer response into the shared plain-text output shape" trick
    // `string-case-convert`'s own onSubmit already establishes above — see
    // formatHashResult's own comment.
    onSubmit: async input => ({ output: formatHashResult(await devUtilsApi.generateHash(input)) }),
  },
  {
    key: 'php-serializer',
    // The fourth ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
    // PhpSerializeOperation/PhpUnserializeOperation — same Encode/Decode pairing shape
    // base64-string/url-string/html-entity-string already established. Genuinely different from
    // `php-to-json`/`json-to-php` above (both Formatters-group "Converters") — those convert
    // between JSON and PHP *array-literal source code* (`['key' => 'value']`); this one converts
    // between JSON and PHP's own serialize()/unserialize() wire format (`a:N:{...}`).
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    // Label/description both name both directions explicitly — "PHP Serializer" alone (the
    // original label) read as one-way, the same "Base64 String"/"HTML Entity" ambiguity
    // `url-string`'s own "URL Encode/Decode" label already avoids; matched that convention here
    // once this operation's own two-button Serialize/Unserialize shape made the gap noticeable.
    label: 'PHP Serializer/Unserializer',
    description: "Serialize JSON into PHP's serialize() format, or unserialize it back into JSON",
    icon: <PhpIcon fontSize="small" />,
    actionLabel: 'Serialize',
    inputPlaceholder: '{"name":"DevKnowledge","active":true,"count":47}',
    // 'text', not 'json' — this input box is shared by both directions, and Unserialize's own
    // input is a PHP serialize() string, not JSON; picking 'json' here would make a failed
    // Unserialize incorrectly try the browser's own JSON.parse fast path first (see
    // errorFormatting.ts#buildDevUtilError), producing a misleading "not valid JSON" message for
    // input that was never meant to be JSON in the first place.
    inputFormat: 'text',
    // Same reasoning in reverse for the output side — Serialize's own output is PHP's serialize
    // format, not JSON, so 'json' would be wrong there too; 'text' is the one shared choice that's
    // never actively misleading for either direction.
    outputLanguage: 'text',
    // No minify option — see `base64-string`/`url-string`/`html-entity-string`'s own comments;
    // PHP's serialize format has no distinct "compact form," and Unserialize's own JSON output is
    // always pretty-printed (see PhpUnserializeOperation's own Javadoc for why that asymmetry
    // with `json-to-php`'s minify support was deliberate).
    supportsMinify: false,
    downloadFileName: 'php-serialized.txt',
    onSubmit: input => devUtilsApi.serializePhp(input),
    secondaryAction: {
      label: 'Unserialize',
      onSubmit: input => devUtilsApi.unserializePhp(input),
    },
  },
  {
    key: 'hex-ascii',
    // The fifth ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
    // AsciiToHexOperation/HexToAsciiOperation — same Encode/Decode pairing shape
    // base64-string/url-string/html-entity-string/php-serializer already established.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    // Both directions named explicitly in the label/description, matching `url-string`'s/
    // `php-serializer`'s own naming convention — see those entries' own comments for why a bare
    // one-directional-sounding name was avoided once this operation gained a real secondary
    // action.
    label: 'ASCII/Hex Converter',
    description: 'Convert text to space-separated hex bytes, or hex bytes back to text',
    icon: <HexIcon fontSize="small" />,
    actionLabel: 'ASCII to Hex',
    inputPlaceholder: 'Serialize JSON',
    inputFormat: 'text',
    outputLanguage: 'text',
    // No minify option — a hex representation has no distinct "compact form" to toggle, same
    // reasoning `base64-string`/`url-string`/`html-entity-string`/`php-serializer` already
    // establish.
    supportsMinify: false,
    downloadFileName: 'hex.txt',
    onSubmit: input => devUtilsApi.encodeHex(input),
    secondaryAction: {
      label: 'Hex to ASCII',
      onSubmit: input => devUtilsApi.decodeHex(input),
    },
  },
  {
    key: 'base64-image',
    // The sixth ENCODERS_DECODERS-group operation, per direct request — but the first to render
    // through its own bespoke component (components/Base64ImagePanel.tsx, the same "some
    // operations need a genuinely different layout" precedent hash-generator already established)
    // instead of the shared DevUtilToolPanel: the input is a *file* (or a pasted Data URL), and
    // the output is a rendered image preview, neither of which fits a plain-text editor pair.
    group: 'Encoders/Decoders',
    category: 'Encoders/Decoders',
    label: 'Base64 Image',
    description: 'Convert images to Data URLs and back, with a live preview',
    icon: <ImageIcon fontSize="small" />,
    // `actionLabel`/`inputFormat`/`outputLanguage`/`supportsMinify`/`downloadFileName`/`onSubmit`
    // below are all unused by Base64ImagePanel (it renders no action button, no code editor, and
    // makes no backend call at all — converting a file to a Data URL is a pure browser
    // `FileReader` operation) but still filled in with reasonable values to satisfy
    // `OperationConfig`'s shared shape, the same "present but inert for this operation" treatment
    // `hash-generator`'s own inputFormat/outputLanguage/supportsMinify/downloadFileName already
    // get (that operation's own custom panel doesn't read them either).
    actionLabel: 'Convert',
    // A real, verified 1x1 transparent PNG's Data URL (confirmed decodable via a standalone Java
    // harness first, not assumed) — short enough to be a reasonable Sample value despite a Data
    // URL's usual length, and immediately shows a real (if tiny) preview when used.
    inputPlaceholder:
      'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    inputFormat: 'text',
    outputLanguage: 'text',
    supportsMinify: false,
    downloadFileName: 'image.txt',
  },
];
