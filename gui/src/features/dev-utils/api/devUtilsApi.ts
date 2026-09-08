import { httpClient } from '@shared/api/httpClient';
import { DevUtilsResponse } from '../types';

type ShowError = (message: string) => void;

// gateway (dev-utils-service is public, no auth — see that service's own SecurityConfig and
// gateway's matching permitAll() carve-out) — one method per backend operation, not a generic
// dispatcher, since one of them (jsonToYaml) genuinely has no `minify` parameter at all.
export const devUtilsApi = {
  formatJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/json/format', { input, minify }, showError);
  },

  yamlToJson(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/yaml-to-json', { input, minify }, showError);
  },

  // No `minify` field at all — the backend operation doesn't support it (jackson-dataformat-yaml
  // has no single-line/flow-style toggle), so the key is never sent.
  jsonToYaml(input: string, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/json-to-yaml', { input }, showError);
  },

  beautifyHtml(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/html/beautify', { input, minify }, showError);
  },

  // The 6 CSS/LESS/SCSS/JS/ERB/XML operations added alongside dev-utils-service's own
  // CurlyBraceFormatter/ErbOperation/XmlOperation — every one of them shares the identical
  // "text in, minify flag, text out" shape `beautifyHtml` already has, so each is just a thin
  // pass-through to its own endpoint, same as every method above.
  beautifyCss(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/css/beautify', { input, minify }, showError);
  },

  beautifyLess(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/less/beautify', { input, minify }, showError);
  },

  beautifyScss(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/scss/beautify', { input, minify }, showError);
  },

  beautifyJs(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/js/beautify', { input, minify }, showError);
  },

  beautifyErb(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/erb/beautify', { input, minify }, showError);
  },

  // The one of the 6 with a real backend validation/error path (dev-utils-service's XmlOperation
  // is JAXP-backed, unlike the lenient CurlyBraceFormatter-based CSS/LESS/SCSS/JS ones or the
  // jsoup-based ERB one) — same shape here regardless, the difference only matters to
  // errorFormatting.ts's own message handling on a failed submit.
  beautifyXml(input: string, minify: boolean, showError?: ShowError): Promise<DevUtilsResponse> {
    return httpClient.post('/api/v1/dev-utils/xml/beautify', { input, minify }, showError);
  },
};
