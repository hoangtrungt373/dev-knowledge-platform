import { httpClient } from '@shared/api/httpClient';
import { DevUtilsResponse } from '../types';

type ShowError = (message: string) => void;

// gateway (dev-utils-service is public, no auth — see that service's own SecurityConfig and
// gateway's matching permitAll() carve-out) — one method per backend operation, not a generic
// dispatcher, since one of the four (jsonToYaml) genuinely has no `minify` parameter at all.
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
};
