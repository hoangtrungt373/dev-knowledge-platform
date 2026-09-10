import { httpClient } from '@shared/api/httpClient';
import { DevUtilsResponse, HashResponse, StringCaseResponse } from '../types';

type ShowError = (message: string) => void;

// gateway (dev-utils-service is public, no auth — see that service's own SecurityConfig and
// gateway's matching permitAll() carve-out).
const BASE = '/api/v1/dev-utils';

// One method per backend operation, not a generic dispatcher, since a few genuinely have a
// different shape (jsonToYaml/jsonToCsv have no `minify` parameter at all; convertStringCase
// returns StringCaseResponse, not DevUtilsResponse) — a shared `post` helper would either have to
// paper over those differences or still need per-method wrappers anyway, so it wouldn't actually
// remove any of this file's real duplication, just the `BASE +` prefix, which the constant above
// already handles.
export const devUtilsApi = {
  formatJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/json/format`, { input, minify }, showError);
  },

  yamlToJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/yaml-to-json`, { input, minify }, showError);
  },

  // No `minify` field at all — the backend operation doesn't support it (jackson-dataformat-yaml
  // has no single-line/flow-style toggle), so the key is never sent.
  jsonToYaml(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/json-to-yaml`, { input }, showError);
  },

  beautifyHtml(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/html/beautify`, { input, minify }, showError);
  },

  // The 6 CSS/LESS/SCSS/JS/ERB/XML operations added alongside dev-utils-service's own
  // CurlyBraceFormatter/ErbOperation/XmlOperation — every one of them shares the identical
  // "text in, minify flag, text out" shape `beautifyHtml` already has, so each is just a thin
  // pass-through to its own endpoint, same as every method above.
  beautifyCss(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/css/beautify`, { input, minify }, showError);
  },

  beautifyLess(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/less/beautify`, { input, minify }, showError);
  },

  beautifyScss(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/scss/beautify`, { input, minify }, showError);
  },

  beautifyJs(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/js/beautify`, { input, minify }, showError);
  },

  beautifyErb(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/erb/beautify`, { input, minify }, showError);
  },

  // The one of the 6 with a real backend validation/error path (dev-utils-service's XmlOperation
  // is JAXP-backed, unlike the lenient CurlyBraceFormatter-based CSS/LESS/SCSS/JS ones or the
  // jsoup-based ERB one) — same shape here regardless, the difference only matters to
  // errorFormatting.ts's own message handling on a failed submit.
  beautifyXml(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/xml/beautify`, { input, minify }, showError);
  },

  // JSON<->CSV + SQL Formatter, added alongside dev-utils-service's own JsonToCsvOperation/
  // CsvToJsonOperation/SqlFormatOperation. jsonToCsv has no `minify` field, same reasoning as
  // jsonToYaml above (CSV has no distinct "compact" form to toggle either).
  jsonToCsv(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/json-to-csv`, { input }, showError);
  },

  // The other operation (besides yamlToJson/beautifyXml) with a real backend invalid-input error
  // path — dev-utils-service's CsvToJsonOperation is backed by a genuine Jackson CsvMapper parser.
  csvToJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/csv-to-json`, { input, minify }, showError);
  },

  formatSql(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/sql/format`, { input, minify }, showError);
  },

  // PHP<->JSON + String Case Converter, added alongside dev-utils-service's own
  // PhpToJsonOperation/JsonToPhpOperation/StringCaseOperation.
  //
  // The 4th operation (besides yamlToJson/beautifyXml/csvToJson) with a real backend
  // invalid-input error path — dev-utils-service's PhpToJsonOperation is backed by a genuine
  // recursive-descent parser (PhpArrayParser).
  phpToJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/php-to-json`, { input, minify }, showError);
  },

  jsonToPhp(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/json-to-php`, { input, minify }, showError);
  },

  // Returns StringCaseResponse, not DevUtilsResponse — the one operation whose output is
  // genuinely richer than a single string (see that type's own comment). No `minify` field —
  // there's no "compact form" of a case conversion.
  convertStringCase(input: string, showError?: ShowError): Promise<StringCaseResponse> {
    return httpClient.post(`${BASE}/string-case/convert`, { input }, showError);
  },

  // The first ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
  // Base64EncodeOperation/Base64DecodeOperation. No `minify` field on either — a Base64 encoding
  // has no distinct "compact form" to toggle, same reasoning `jsonToYaml`/`jsonToCsv` already
  // establish. Two separate methods (not one "base64" method with a direction flag), matching how
  // every other bidirectional pair in this file (`phpToJson`/`jsonToPhp`, `yamlToJson`/
  // `jsonToYaml`) already gets one method per direction.
  encodeBase64(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/base64/encode`, { input }, showError);
  },

  decodeBase64(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/base64/decode`, { input }, showError);
  },

  // The second ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
  // UrlEncodeOperation/UrlDecodeOperation. No `minify` field on either — same reasoning
  // `encodeBase64`/`decodeBase64` already establish; a percent-encoding has no distinct "compact
  // form" to toggle.
  encodeUrl(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/url/encode`, { input }, showError);
  },

  decodeUrl(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/url/decode`, { input }, showError);
  },

  // The third ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
  // HtmlEntityEncodeOperation/HtmlEntityDecodeOperation. No `minify` field on either — same
  // reasoning `encodeBase64`/`encodeUrl` already establish; an escaped form has no distinct
  // "compact form" to toggle.
  encodeHtmlEntity(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/html-entity/encode`, { input }, showError);
  },

  decodeHtmlEntity(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/html-entity/decode`, { input }, showError);
  },

  // Added alongside dev-utils-service's own HashGeneratorOperation (originally the first
  // INSPECTORS-group operation, later moved to ENCODERS_DECODERS — see that class's own Javadoc).
  // Returns HashResponse, not DevUtilsResponse — the second operation (after convertStringCase)
  // whose output is genuinely richer than a single string. No `minify` field — a hash digest has
  // no distinct "compact form" to toggle.
  generateHash(input: string, showError?: ShowError): Promise<HashResponse> {
    return httpClient.post(`${BASE}/hash/generate`, { input }, showError);
  },

  // The fourth ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
  // PhpSerializeOperation/PhpUnserializeOperation. No `minify` field on either — same reasoning
  // `encodeBase64`/`encodeUrl`/`encodeHtmlEntity` already establish; PHP's serialize() format has
  // no distinct "compact form" to toggle, and unserializePhp's own JSON output is always
  // pretty-printed (see that backend operation's own Javadoc for why it deliberately doesn't
  // support minify the way `phpToJson` does).
  serializePhp(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/php-serialize/serialize`, { input }, showError);
  },

  unserializePhp(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/php-serialize/unserialize`, { input }, showError);
  },

  // The fifth ENCODERS_DECODERS-group operation, added alongside dev-utils-service's own
  // AsciiToHexOperation/HexToAsciiOperation. No `minify` field on either — same reasoning
  // `encodeBase64`/`encodeUrl`/`encodeHtmlEntity`/`serializePhp` already establish; a hex
  // representation has no distinct "compact form" to toggle.
  encodeHex(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/hex/encode`, { input }, showError);
  },

  decodeHex(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/hex/decode`, { input }, showError);
  },

  // The first INSPECTORS-group operation, added alongside dev-utils-service's own
  // JwtDebuggerOperation. Shares MinifiableTextRequest's own "pretty vs. minify" shape — unlike
  // every ENCODERS_DECODERS pair above, this operation's output (a JSON object) genuinely has a
  // "compact form" to toggle, the same reasoning `formatJson`/`beautifyXml` etc. already establish.
  debugJwt(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/jwt/debug`, { input, minify }, showError);
  },

  // The second INSPECTORS-group operation, added alongside dev-utils-service's own
  // RegexTesterOperation. Genuinely 3 separate fields, not `input`/`minify` — see that backend
  // operation's own `RegexTestRequest` DTO Javadoc for why this needed its own dedicated shape
  // rather than being bent into the (input, minify) convention every method above shares.
  testRegexp(pattern: string, flags: string, testText: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/regexp/test`, { pattern, flags, testText }, showError);
  },

  // The first WEB-group operation, added alongside dev-utils-service's own UrlParserOperation.
  // Back to the plain (input, minify) shape every Formatters-group operation already shares — its
  // output (protocol/hostname/port/etc., plus a parsed query object) is a single JSON string like
  // any other, so it renders through the shared DevUtilToolPanel with no bespoke panel needed.
  parseUrl(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/url/parse`, { input, minify }, showError);
  },

  // The third INSPECTORS-group operation, added alongside dev-utils-service's own
  // CronParserOperation. No `minify` field — same reasoning `encodeBase64`/`generateHash`/etc.
  // already establish; a plain-English description has no distinct "compact form" to toggle.
  parseCron(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post(`${BASE}/cron/parse`, { input }, showError);
  },
};
